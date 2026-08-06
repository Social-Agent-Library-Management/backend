package org.library.core.exception

import io.github.oshai.kotlinlogging.KotlinLogging
import org.hibernate.exception.ConstraintViolationException
import org.library.core.logging.TraceIdFilter
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class SystemExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ProblemDetail =
        problem(e.errorCode.status, e.errorCode.code, e.errorCode.message)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ProblemDetail {
        val kind = (e.cause as? ConstraintViolationException)?.kind
        if (kind != ConstraintViolationException.ConstraintKind.UNIQUE) return handleUnexpected(e)

        log.warn(e) { "Unique constraint violation" }
        return problem(
            CommonErrorCode.DATA_CONFLICT.status,
            CommonErrorCode.DATA_CONFLICT.code,
            CommonErrorCode.DATA_CONFLICT.message,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ProblemDetail {
        log.error(e) { "Unhandled exception" }
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.INTERNAL_ERROR.code,
            CommonErrorCode.INTERNAL_ERROR.message,
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val detail = ex.bindingResult.fieldErrors.joinToString(", ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return ResponseEntity.badRequest().body(
            problem(
                HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_INPUT.code,
                detail.ifBlank { CommonErrorCode.INVALID_INPUT.message },
            ),
        )
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val status = HttpStatus.valueOf(statusCode.value())
        if (status.is5xxServerError) {
            log.error(ex) { "Framework server error" }
        } else {
            log.warn { "Framework request error(${status.value()}): ${ex.message}" }
        }
        val detail = (body as? ProblemDetail)?.detail ?: status.reasonPhrase
        return ResponseEntity.status(status)
            .headers(headers)
            .body(problem(status, status.name, detail))
    }

    private fun problem(status: HttpStatus, code: String, message: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, message).apply {
            title = code
            setProperty("code", code)
            setProperty("traceId", MDC.get(TraceIdFilter.TRACE_ID))
        }
}
