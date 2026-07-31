package org.library.core.exception

import org.springframework.http.HttpStatus

interface ErrorCode {
    val status: HttpStatus
    val code: String
    val message: String
}

fun ErrorCode.toException(): DomainException = DomainException(this)
