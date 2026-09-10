package org.library.book.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.core.presentation.PageRequestParams
import org.library.core.presentation.Pagination
import org.springframework.stereotype.Service

@Service
class SearchBooksService(
    private val bookSearchPort: BookSearchPort,
) {

    fun execute(query: String?, params: PageRequestParams): Response {
        val result = bookSearchPort.search(query, params)
        return Response(
            books = result.page.content,
            pagination = Pagination.from(result.page),
            suggestion = result.suggestion,
        )
    }

    @Schema(name = "BookSearchResponse", description = "도서 목록·검색 응답")
    data class Response(
        val books: List<BookDocument>,
        val pagination: Pagination,
        @Schema(description = "검색어가 오타로 판단될 때의 교정 제안어(없으면 null). 결과 건수와 무관하게 내려간다.")
        val suggestion: String?,
    )
}
