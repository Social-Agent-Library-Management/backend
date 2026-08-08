package org.library.loan.domain.error

import org.library.core.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class LoanError(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    BOOK_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "소장본을 찾을 수 없습니다."),
    BOOK_ITEM_NOT_AVAILABLE(HttpStatus.CONFLICT, "대출할 수 없는 소장본입니다."),
    ;

    override val code: String get() = name
}
