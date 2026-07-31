package org.library.core.presentation

import org.springframework.data.domain.Page

data class Pagination(
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalElements: Long,
) {
    companion object {
        fun from(page: Page<*>): Pagination = Pagination(
            page = page.number + 1,
            pageSize = page.size,
            totalPages = page.totalPages,
            totalElements = page.totalElements,
        )
    }
}
