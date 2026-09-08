package org.library.excel.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.library.core.application.getOrThrow
import org.library.core.swagger.ApiErrorCode
import org.library.excel.application.ImportExcelService
import org.library.excel.domain.error.ExcelImportError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Excel", description = "엑셀 관리 API")
@RestController
class ExcelController(
    private val importExcelService: ImportExcelService,
) {

    @Operation(
        summary = "엑셀 일괄 등록",
        description = "엑셀 파일의 모든 시트를 검증 후 도서·소장본으로 등록한다. 오류 행이 하나라도 있으면 전체가 등록되지 않는다(all-or-nothing).",
    )
    @ApiErrorCode(
        errorCodes = [ExcelImportError::class],
        only = ["INVALID_FILE_TYPE", "UNREADABLE_FILE", "REQUIRED_COLUMN_NOT_FOUND", "FILE_TOO_LARGE", "VALIDATION_ERRORS_REMAIN"],
    )
    @PostMapping("/imports/excel", consumes = ["multipart/form-data"])
    fun importExcel(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportExcelService.Response> {
        val response = importExcelService.execute(file).getOrThrow()
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
