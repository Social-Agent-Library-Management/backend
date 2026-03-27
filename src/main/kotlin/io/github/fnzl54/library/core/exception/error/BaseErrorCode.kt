package io.github.fnzl54.library.core.exception.error

import org.springframework.http.HttpStatus

interface BaseErrorCode<T : RuntimeException> {
    val name: String
    val message: String
    val httpStatus: HttpStatus

    fun toException(): T
}
