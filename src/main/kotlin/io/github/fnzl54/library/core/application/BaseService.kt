package io.github.fnzl54.library.core.application

import io.github.fnzl54.library.core.exception.error.GlobalErrorCode

abstract class BaseService<Q : BaseRequest, R : BaseResponse<*>> {
    fun execute(request: Q): R {
        if (!request.isValid) {
            throw GlobalErrorCode.INVALID_REQUEST.toException()
        }
        return doExecute(request)
    }

    protected abstract fun doExecute(request: Q): R
}
