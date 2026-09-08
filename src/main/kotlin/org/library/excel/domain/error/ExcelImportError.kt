package org.library.excel.domain.error

import org.library.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ExcelImportError(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 확장자입니다."),
    UNREADABLE_FILE(HttpStatus.BAD_REQUEST, "파일이 손상되어 읽을 수 없습니다."),
    REQUIRED_COLUMN_NOT_FOUND(HttpStatus.BAD_REQUEST, "도서번호·도서명 머리글을 찾지 못했습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 20MB를 초과했습니다."),
    VALIDATION_ERRORS_REMAIN(HttpStatus.CONFLICT, "오류 행이 있어 등록을 진행할 수 없습니다."),
    ;

    override val code: String get() = name
}
