package org.library.core.application

import org.library.core.exception.ErrorCode
import org.library.core.exception.toException

sealed interface Result<out T, out E : ErrorCode> {
    data class Ok<out T>(val data: T) : Result<T, Nothing>
    data class Err<out E : ErrorCode>(val error: E) : Result<Nothing, E>
}

fun <T, E : ErrorCode> Result<T, E>.getOrThrow(): T = when (this) {
    is Result.Ok -> data
    is Result.Err -> throw error.toException()
}

inline fun <T, E : ErrorCode, R> Result<T, E>.fold(
    onOk: (T) -> R,
    onErr: (E) -> R,
): R = when (this) {
    is Result.Ok -> onOk(data)
    is Result.Err -> onErr(error)
}

fun <T> T.ok(): Result<T, Nothing> = Result.Ok(this)
fun <E : ErrorCode> E.err(): Result<Nothing, E> = Result.Err(this)
