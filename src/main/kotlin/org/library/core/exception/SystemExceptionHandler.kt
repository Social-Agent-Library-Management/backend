package org.library.core.exception

import io.github.oshai.kotlinlogging.KotlinLogging
import org.library.core.logging.TraceIdFilter
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class SystemExceptionHandler {

    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ProblemDetail =
        problem(e.errorCode.status, e.errorCode.code, e.errorCode.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ProblemDetail {
        val detail = e.bindingResult.fieldErrors.joinToString(", ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return problem(
            HttpStatus.BAD_REQUEST,
            CommonErrorCode.INVALID_INPUT.code,
            detail.ifBlank { CommonErrorCode.INVALID_INPUT.message },
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException): ProblemDetail =
        problem(
            HttpStatus.METHOD_NOT_ALLOWED,
            CommonErrorCode.METHOD_NOT_ALLOWED.code,
            CommonErrorCode.METHOD_NOT_ALLOWED.message,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ProblemDetail {
        log.error(e) { "Unhandled exception" }
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.INTERNAL_ERROR.code,
            CommonErrorCode.INTERNAL_ERROR.message,
        )
    }

    private fun problem(status: HttpStatus, code: String, message: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, message).apply {
            title = code
            setProperty("code", code)
            setProperty("traceId", MDC.get(TraceIdFilter.TRACE_ID))
        }
}
