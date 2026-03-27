package io.github.fnzl54.library.core.exception.error

import io.github.fnzl54.library.core.exception.DomainException
import org.springframework.http.HttpStatus

enum class GlobalErrorCode(
    override val message: String,
    override val httpStatus: HttpStatus,
) : BaseErrorCode<DomainException> {
    INVALID_REQUEST("요청 파라미터가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    override fun toException(): DomainException = DomainException.from(this)
}
