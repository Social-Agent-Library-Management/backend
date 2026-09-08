package org.library.excel.application

import io.swagger.v3.oas.annotations.media.Schema
import org.apache.poi.ss.usermodel.Workbook
import org.library.book.application.event.BookChangedEvent
import org.library.book.domain.entity.Book
import org.library.book.domain.repository.BookRepository
import org.library.bookitem.domain.BookItem
import org.library.bookitem.domain.BookItemRepository
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.library.excel.domain.ExcelImportRowCode
import org.library.excel.domain.ExcelImportRowJudgement
import org.library.excel.domain.error.ExcelError
import org.library.excel.domain.error.ExcelImportRejectedException
import org.library.excel.dto.ExcelImportRowResult
import org.library.excel.mapping.ExcelColumnMapper
import org.library.excel.reader.ExcelWorkbookReader
import org.library.excel.validation.ExcelImportRowValidator
import org.library.excel.validation.IdentityKeyNormalizer
import org.library.excel.validation.ManagementNumberNormalizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@Service
class ImportExcelService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
    private val excelImportRowValidator: ExcelImportRowValidator,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value("\${excel.import.max-file-size-bytes:20971520}") private val maxFileSizeBytes: Long,
) {

    @Transactional
    fun execute(file: MultipartFile): Result<Response, ExcelError> {
        val fileName = file.originalFilename?.takeIf { it.isNotBlank() } ?: file.name
        if (!ExcelWorkbookReader.isSupportedFileType(fileName)) return ExcelError.INVALID_FILE_TYPE.err()
        if (file.size > maxFileSizeBytes) return ExcelError.FILE_TOO_LARGE.err()

        val bytes = runCatching { file.bytes }.getOrNull() ?: return ExcelError.UNREADABLE_FILE.err()
        val workbook = runCatching { ExcelWorkbookReader.open(bytes) }.getOrNull() ?: return ExcelError.UNREADABLE_FILE.err()

        return workbook.use {
            val sheetNames = ExcelWorkbookReader.sheetNames(workbook)
            if (sheetNames.isEmpty()) return ExcelError.UNREADABLE_FILE.err()

            val firstSheet = workbook.getSheet(sheetNames[0])
            val mappedColumns = ExcelColumnMapper.detect(ExcelWorkbookReader.headerRow(firstSheet))
                ?: return ExcelError.REQUIRED_COLUMN_NOT_FOUND.err()

            processRows(workbook, sheetNames, mappedColumns).ok()
        }
    }

    private fun processRows(
        workbook: Workbook,
        sheetNames: List<String>,
        mappedColumns: ExcelColumnMapper.MappedColumns,
    ): Response {
        val rawRows = readRawRows(workbook, sheetNames, mappedColumns)

        val identityIndex = bookRepository.findAllActiveIdentityProjection()
            .associate { IdentityKeyNormalizer.normalize(it.title, it.publisher, it.author) to it.id }
            .toMutableMap()

        val candidateManagementNumbers = rawRows.mapNotNullTo(mutableSetOf()) { resolveCandidateManagementNumber(it.managementNumber) }
        val existingManagementNumbers = if (candidateManagementNumbers.isEmpty()) {
            emptySet()
        } else {
            bookItemRepository.findExistingManagementNumbers(candidateManagementNumbers).toSet()
        }

        val managementNumbersSeenInFile = mutableMapOf<String, Int>()
        val identityKeysSeenInFile = mutableSetOf<String>()
        val errorRows = mutableListOf<ExcelImportRowResult>()
        val warnRows = mutableListOf<ExcelImportRowResult>()
        val pendingCommits = mutableListOf<PendingRow>()
        var okCount = 0

        for (row in rawRows) {
            val outcome = excelImportRowValidator.validate(
                input = ExcelImportRowValidator.RowInput(row.managementNumber, row.title, row.publisher, row.author),
                managementNumbersSeenInFile = managementNumbersSeenInFile,
                identityKeysSeenInFile = identityKeysSeenInFile,
                managementNumberExistsInDb = { mgmt -> mgmt in existingManagementNumbers },
                existingBookIdByIdentityKey = { key -> identityIndex[key] },
            )

            if (outcome.judgement == ExcelImportRowJudgement.ERROR) {
                errorRows += ExcelImportRowResult(row.sheetName, row.rowNumber, row.managementNumber, row.title, outcome.code, outcome.message, outcome.errorField)
                continue
            }

            outcome.normalizedManagementNumber?.let { managementNumbersSeenInFile[it] = row.rowNumber }
            if (outcome.code != ExcelImportRowCode.EXISTING_BOOK_ITEM_ADDED) {
                identityKeysSeenInFile += outcome.identityKey
            }

            if (outcome.judgement == ExcelImportRowJudgement.WARN) {
                warnRows += ExcelImportRowResult(row.sheetName, row.rowNumber, row.managementNumber, row.title, outcome.code, outcome.message, outcome.errorField)
            } else {
                okCount++
            }

            pendingCommits += PendingRow(
                title = row.title,
                publisher = row.publisher,
                author = row.author,
                identityKey = outcome.identityKey,
                resolvedManagementNumber = outcome.normalizedManagementNumber ?: row.managementNumber,
            )
        }

        if (errorRows.isNotEmpty()) {
            throw ExcelImportRejectedException(errorRows = errorRows.size, errors = errorRows)
        }

        var createdBooksCount = 0
        val changedBookIds = mutableSetOf<Long>()

        for (row in pendingCommits) {
            val bookId = identityIndex[row.identityKey] ?: run {
                val title = requireNotNull(row.title).trim()
                val publisher = requireNotNull(row.publisher).trim()
                val author = requireNotNull(row.author).trim()
                val book = bookRepository.save(Book(title = title, author = author, isbn = null, publisher = publisher))
                identityIndex[row.identityKey] = book.id
                createdBooksCount++
                book.id
            }
            val managementNumber = requireNotNull(row.resolvedManagementNumber)
            bookItemRepository.save(BookItem(bookId = bookId, managementNumber = managementNumber))
            changedBookIds += bookId
        }

        changedBookIds.forEach { applicationEventPublisher.publishEvent(BookChangedEvent(it)) }

        return Response(
            committedAt = LocalDateTime.now(),
            sheets = sheetNames,
            totalRows = rawRows.size,
            createdBooks = createdBooksCount,
            createdBookItems = pendingCommits.size,
            okRows = okCount,
            warnRows = warnRows.size,
            warnings = warnRows,
        )
    }

    private fun readRawRows(
        workbook: Workbook,
        sheetNames: List<String>,
        mappedColumns: ExcelColumnMapper.MappedColumns,
    ): List<RawRow> {
        val rows = mutableListOf<RawRow>()
        for (sheetName in sheetNames) {
            val sheet = workbook.getSheet(sheetName)
            for ((rowNumber, values) in ExcelWorkbookReader.dataRows(sheet)) {
                rows += RawRow(
                    sheetName = sheetName,
                    rowNumber = rowNumber,
                    managementNumber = values.getOrNull(mappedColumns.managementNumberIndex),
                    title = values.getOrNull(mappedColumns.titleIndex),
                    publisher = mappedColumns.publisherIndex?.let { values.getOrNull(it) },
                    author = mappedColumns.authorIndex?.let { values.getOrNull(it) },
                )
            }
        }
        return rows
    }

    private fun resolveCandidateManagementNumber(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val result = ManagementNumberNormalizer.normalize(trimmed)
        return (result as? ManagementNumberNormalizer.NormalizationResult.AlreadyValid)?.value
    }

    private data class RawRow(
        val sheetName: String,
        val rowNumber: Int,
        val managementNumber: String?,
        val title: String?,
        val publisher: String?,
        val author: String?,
    )

    private data class PendingRow(
        val title: String?,
        val publisher: String?,
        val author: String?,
        val identityKey: String,
        val resolvedManagementNumber: String?,
    )

    @Schema(name = "ImportExcelResponse", description = "등록 성공")
    data class Response(
        @field:Schema(description = "등록 완료 시각")
        val committedAt: LocalDateTime,
        @field:Schema(description = "처리된 시트 목록")
        val sheets: List<String>,
        @field:Schema(description = "전체 처리 행 수", example = "4838")
        val totalRows: Int,
        @field:Schema(description = "생성된 도서 수", example = "2874")
        val createdBooks: Int,
        @field:Schema(description = "생성된 소장본 수", example = "4838")
        val createdBookItems: Int,
        @field:Schema(description = "정상 행 수", example = "4700")
        val okRows: Int,
        @field:Schema(description = "경고(복본 추가 등) 행 수", example = "138")
        val warnRows: Int,
        @field:Schema(description = "경고가 있었던 행 상세 목록 (검토용)")
        val warnings: List<ExcelImportRowResult>,
    )
}
