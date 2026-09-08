package org.library.excel.domain.error

import org.library.excel.dto.ExcelImportRowResult

class ExcelImportRejectedException(
    val errorRows: Int,
    val errors: List<ExcelImportRowResult>,
) : RuntimeException(ExcelImportError.VALIDATION_ERRORS_REMAIN.message) {

    val errorCode: ExcelImportError = ExcelImportError.VALIDATION_ERRORS_REMAIN
}
