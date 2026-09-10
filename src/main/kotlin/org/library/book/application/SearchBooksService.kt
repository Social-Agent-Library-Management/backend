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
            suggestions = result.suggestions,
        )
    }

    @Schema(name = "BookSearchResponse", description = "도서 목록·검색 응답")
    data class Response(
        val books: List<BookDocument>,
        val pagination: Pagination,
        @Schema(
            description = "검색 결과가 0건일 때 내려가는 교정 검색어(최대 3건, 없으면 빈 배열). " +
                "책 제목이 아니라 **다시 검색할 키워드**이므로 그대로 재검색하면 된다. " +
                "확신이 높은 순서이며, books 와 달리 원래 검색어를 부분 문자열로 포함하지 않는 추측이다.",
            example = "[\"기억\"]",
        )
        val suggestions: List<String>,
    )
}
