package io.github.fnzl54.library.core.exception

import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import org.springframework.http.HttpStatus

class DomainException(
    val httpStatus: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun from(errorCode: BaseErrorCode<*>): DomainException =
            DomainException(
                httpStatus = errorCode.httpStatus,
                code = errorCode.name,
                message = errorCode.message,
            )
    }
}
