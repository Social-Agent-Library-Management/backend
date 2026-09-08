package org.library.excel.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.library.excel.domain.ExcelImportRowCode

@Schema(name = "ImportExcelRowResult")
data class ExcelImportRowResult(
    @field:Schema(description = "시트명", example = "문학(신규)")
    val sheet: String,
    @field:Schema(description = "원본 행 번호", example = "104")
    val rowNumber: Int,
    @field:Schema(description = "관리번호", example = "문학-2", nullable = true)
    val managementNumber: String?,
    @field:Schema(description = "도서명", example = "말의 힘", nullable = true)
    val title: String?,
    @field:Schema(description = "판정 코드", nullable = true)
    val code: ExcelImportRowCode?,
    @field:Schema(description = "메시지", nullable = true)
    val message: String?,
    @field:Schema(description = "오류 필드", nullable = true)
    val errorField: String?,
)
