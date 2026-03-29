package io.github.fnzl54.library.config.swagger

import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrorCode(
    val errorCodes: Array<KClass<out BaseErrorCode<*>>>,
)
