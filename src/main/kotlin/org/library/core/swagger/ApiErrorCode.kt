package org.library.core.swagger

import org.library.core.exception.ErrorCode
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrorCode(
    val errorCodes: Array<KClass<out ErrorCode>>,
    val only: Array<String> = [],
)
