package io.github.fnzl54.library.core.presentation.pagination

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

data class Pagination(
    @Schema(description = "현재 페이지 (1부터 시작)", example = "1")
    val page: Int,
    @Schema(description = "페이지 크기", example = "10")
    val size: Int,
    @Schema(description = "전체 페이지 수", example = "5")
    val totalPages: Int,
    @Schema(description = "전체 요소 수", example = "50")
    val totalElements: Long,
) {
    companion object {
        fun from(page: Page<*>): Pagination =
            Pagination(
                page = page.number + 1,
                size = page.size,
                totalPages = page.totalPages,
                totalElements = page.totalElements,
            )
    }
}
