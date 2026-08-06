package org.library.book.domain.error

import org.library.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class BookError(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "도서를 찾을 수 없습니다."),
    DUPLICATE_ISBN(HttpStatus.CONFLICT, "이미 등록된 ISBN입니다."),
    ;

    override val code: String get() = name
}
