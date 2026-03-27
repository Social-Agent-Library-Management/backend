package io.github.fnzl54.library.core.application

import com.fasterxml.jackson.annotation.JsonIgnore

interface BaseRequest {
    @get:JsonIgnore
    val isValid: Boolean
}
