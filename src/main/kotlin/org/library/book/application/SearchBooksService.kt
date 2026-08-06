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
        val page = bookSearchPort.search(query, params)
        return Response(
            books = page.content,
            pagination = Pagination.from(page),
        )
    }

    @Schema(name = "BookSearchResponse", description = "도서 목록·검색 응답")
    data class Response(
        val books: List<BookDocument>,
        val pagination: Pagination,
    )
}
