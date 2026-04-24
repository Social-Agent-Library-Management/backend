package io.github.fnzl54.library.core.presentation.pagination

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.PageRequest

open class BasePaginationRequest(
    @field:Schema(description = "페이지 번호 (1부터 시작, 기본값: 1)", example = "1")
    open val page: Int = DEFAULT_PAGE,
    @field:Schema(description = "페이지 크기 (기본값: 10, 최대: 100)", example = "10")
    open val size: Int = DEFAULT_SIZE,
) {
    fun toPageRequest(): PageRequest =
        PageRequest.of(
            page.coerceAtLeast(1) - 1,
            size.coerceIn(1, MAX_SIZE),
        )

    companion object {
        private const val DEFAULT_PAGE = 1
        private const val DEFAULT_SIZE = 10
        private const val MAX_SIZE = 100
    }
}
