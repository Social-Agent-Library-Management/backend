package org.library.excel.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.library.core.application.getOrThrow
import org.library.core.swagger.ApiErrorCode
import org.library.excel.application.ExportExcelService
import org.library.excel.application.ExportExcelService.ExcelExportSheetType
import org.library.excel.application.ImportExcelService
import org.library.excel.domain.error.ExcelError
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@Tag(name = "Excel", description = "엑셀 관리 API")
@RestController
class ExcelController(
    private val importExcelService: ImportExcelService,
    private val exportExcelService: ExportExcelService,
) {

    @Operation(
        summary = "엑셀 일괄 등록",
        description = "엑셀 파일의 모든 시트를 검증 후 도서·소장본으로 등록한다. 오류 행이 하나라도 있으면 전체가 등록되지 않는다(all-or-nothing).",
    )
    @ApiErrorCode(
        errorCodes = [ExcelError::class],
        only = ["INVALID_FILE_TYPE", "UNREADABLE_FILE", "REQUIRED_COLUMN_NOT_FOUND", "FILE_TOO_LARGE", "VALIDATION_ERRORS_REMAIN"],
    )
    @PostMapping("/imports/excel", consumes = ["multipart/form-data"])
    fun importExcel(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportExcelService.Response> {
        val response = importExcelService.execute(file).getOrThrow()
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "데이터 내보내기",
        description = "선택한 시트(도서/소장본/대여)를 정규화한 엑셀 파일로 즉시 내려준다.",
    )
    @ApiErrorCode(errorCodes = [ExcelError::class], only = ["NO_SHEET_SELECTED", "INVALID_DATE_RANGE"])
    @GetMapping("/exports/excel")
    fun exportExcel(
        @Parameter(description = "내보낼 시트") @RequestParam(required = false) sheets: List<ExcelExportSheetType>?,
        @Parameter(description = "대여일 시작(대여 시트에만 적용)") @RequestParam(required = false) loanDateFrom: LocalDate?,
        @Parameter(description = "대여일 종료") @RequestParam(required = false) loanDateTo: LocalDate?,
        @Parameter(description = "분실·폐기 소장본 포함") @RequestParam(required = false, defaultValue = "true") includeInactiveItems: Boolean,
        @Parameter(description = "미반납 대출 건만") @RequestParam(required = false, defaultValue = "false") unreturnedOnly: Boolean,
    ): ResponseEntity<ByteArray> {
        val export = exportExcelService.execute(
            sheets = sheets ?: emptyList(),
            loanDateFrom = loanDateFrom,
            loanDateTo = loanDateTo,
            includeInactiveItems = includeInactiveItems,
            unreturnedOnly = unreturnedOnly,
        ).getOrThrow()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName).build().toString())
            .body(export.content)
    }
}
