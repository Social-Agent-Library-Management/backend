package org.library.excel.domain.error

import org.library.excel.dto.ExcelImportRowResult

class ExcelImportRejectedException(
    val errorRows: Int,
    val errors: List<ExcelImportRowResult>,
) : RuntimeException(ExcelError.VALIDATION_ERRORS_REMAIN.message) {

    val errorCode: ExcelError = ExcelError.VALIDATION_ERRORS_REMAIN
}
