package org.library.bookitem.domain.error

import org.library.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class BookItemError(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "도서를 찾을 수 없습니다."),
    INVALID_MANAGEMENT_NUMBER_FORMAT(HttpStatus.BAD_REQUEST, "관리번호 형식이 올바르지 않습니다. (주제)-(번호) 형식으로 입력하세요. 예: 문학-0001"),
    DUPLICATE_MANAGEMENT_NUMBER(HttpStatus.CONFLICT, "이미 존재하는 관리번호입니다."),
    ;

    override val code: String get() = name
}
