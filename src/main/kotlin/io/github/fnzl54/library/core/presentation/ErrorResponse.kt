package io.github.fnzl54.library.core.presentation

import io.github.fnzl54.library.core.application.ExternalResponse
import io.github.fnzl54.library.core.exception.DomainException
import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import java.time.LocalDateTime

data class ErrorResponse(
    override val statusCode: Int,
    val error: Error,
) : ExternalResponse {
    data class Error(
        val type: String,
        val message: String,
        val timeStamp: String,
    )

    companion object {
        fun from(exception: DomainException): ErrorResponse =
            ErrorResponse(
                statusCode = exception.httpStatus.value(),
                error =
                    Error(
                        type = exception.code,
                        message = exception.message,
                        timeStamp = LocalDateTime.now().toString(),
                    ),
            )

        fun from(errorCode: BaseErrorCode<*>): ErrorResponse =
            ErrorResponse(
                statusCode = errorCode.httpStatus.value(),
                error =
                    Error(
                        type = errorCode.name,
                        message = errorCode.message,
                        timeStamp = LocalDateTime.now().toString(),
                    ),
            )
    }
}
