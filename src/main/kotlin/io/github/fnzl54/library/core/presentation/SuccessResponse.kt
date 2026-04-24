package io.github.fnzl54.library.core.presentation

import io.github.fnzl54.library.core.application.BaseResponse

open class SuccessResponse<E>(
    override val statusCode: Int,
    result: E,
) : ExternalResponse, BaseResponse<E>(result = result)
