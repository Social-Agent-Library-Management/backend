package org.library.core.presentation

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

data class PageRequestParams(
    val page: Int? = null,
    val pageSize: Int? = null,
) {
    fun toPageRequest(sort: Sort = Sort.unsorted()): PageRequest {
        val validatedPage = if (page == null || page < 1) DEFAULT_PAGE else page
        val validatedPageSize = when {
            pageSize == null || pageSize < 1 -> DEFAULT_PAGE_SIZE
            else -> minOf(pageSize, MAX_PAGE_SIZE)
        }
        return PageRequest.of(validatedPage - 1, validatedPageSize, sort)
    }

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_PAGE_SIZE = 10
        const val MAX_PAGE_SIZE = 100
    }
}
