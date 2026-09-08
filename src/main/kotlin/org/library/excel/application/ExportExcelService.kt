package org.library.excel.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.book.domain.repository.BookRepository
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.library.excel.domain.error.ExcelError
import org.library.excel.writer.ExcelWorkbookWriter
import org.library.loan.domain.LoanRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ExportExcelService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
    private val loanRepository: LoanRepository,
) {

    fun execute(
        sheets: List<ExcelExportSheetType>,
        loanDateFrom: LocalDate?,
        loanDateTo: LocalDate?,
        includeInactiveItems: Boolean,
        unreturnedOnly: Boolean,
    ): Result<Export, ExcelError> {
        if (sheets.isEmpty()) return ExcelError.NO_SHEET_SELECTED.err()
        if (ExcelExportSheetType.LOANS in sheets &&
            loanDateFrom != null && loanDateTo != null && loanDateFrom.isAfter(loanDateTo)
        ) {
            return ExcelError.INVALID_DATE_RANGE.err()
        }

        val sheetDataList = mutableListOf<ExcelWorkbookWriter.SheetData>()

        if (ExcelExportSheetType.BOOKS in sheets) {
            val books = bookRepository.findAllByDeletedAtIsNullOrderByIdAsc()
            sheetDataList += ExcelWorkbookWriter.SheetData(
                name = ExcelExportSheetType.BOOKS.koreanLabel,
                headers = listOf("도서ID", "도서명", "저자", "출판사", "ISBN", "등록일시"),
                rows = books.map { listOf(it.id, it.title, it.author, it.publisher, it.isbn, it.createdAt) },
            )
        }

        if (ExcelExportSheetType.BOOK_ITEMS in sheets) {
            val bookItems = if (includeInactiveItems) {
                bookItemRepository.findAllByDeletedAtIsNullOrderByIdAsc()
            } else {
                bookItemRepository.findAllByDeletedAtIsNullAndStatusNotInOrderByIdAsc(INACTIVE_STATUSES)
            }
            sheetDataList += ExcelWorkbookWriter.SheetData(
                name = ExcelExportSheetType.BOOK_ITEMS.koreanLabel,
                headers = listOf("소장본ID", "도서ID", "관리번호", "상태", "등록일시"),
                rows = bookItems.map { listOf(it.id, it.bookId, it.managementNumber, it.status.name, it.createdAt) },
            )
        }

        if (ExcelExportSheetType.LOANS in sheets) {
            val loans = loanRepository.findAllForExport(loanDateFrom, loanDateTo, unreturnedOnly)
            sheetDataList += ExcelWorkbookWriter.SheetData(
                name = ExcelExportSheetType.LOANS.koreanLabel,
                headers = listOf(
                    "대여ID", "소장본ID", "관리번호", "도서명", "대출자", "부서", "이메일", "대출일", "반납예정일", "반납일", "상태",
                ),
                rows = loans.map {
                    listOf(
                        it.id, it.bookItemId, it.managementNumber, it.bookTitle, it.borrowerName, it.department,
                        it.borrowerEmail, it.loanDate, it.dueDate, it.returnedAt, it.status.name,
                    )
                },
            )
        }

        val content = ExcelWorkbookWriter.build(sheetDataList)
        return Export(fileName = "library_export_${LocalDate.now()}.xlsx", content = content).ok()
    }

    @Schema(name = "ExcelExportSheetType", description = "내보내기 대상 시트")
    enum class ExcelExportSheetType(val koreanLabel: String) {
        BOOKS("도서"),
        BOOK_ITEMS("소장본"),
        LOANS("대여"),
    }

    data class Export(val fileName: String, val content: ByteArray)

    companion object {
        private val INACTIVE_STATUSES = listOf(BookItemStatus.LOST, BookItemStatus.DISPOSED)
    }
}
