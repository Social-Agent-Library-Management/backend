package org.library.bookitem.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.book.domain.BookRepository
import org.library.bookitem.domain.BookItem
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
import org.library.core.presentation.PageRequestParams
import org.library.core.presentation.Pagination
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SearchBookItemsService(
    private val bookItemRepository: BookItemRepository,
    private val bookRepository: BookRepository,
) {

    fun execute(q: String?, managementNumber: String?, params: PageRequestParams): Response {
        val pageRequest = params.toPageRequest(Sort.by(Sort.Direction.DESC, "createdAt"))
        val matchedBookIds = q?.let { bookRepository.findAllByTitleContainingOrAuthorContaining(it).map { book -> book.id } }
        val page = bookItemRepository.search(
            bookIds = matchedBookIds,
            managementNumber = managementNumber,
            pageable = pageRequest,
        )
        val bookTitlesById = bookRepository.findAllById(page.content.map { it.bookId }.distinct())
            .associate { it.id to it.title }
        return Response(
            bookItems = page.content.map { BookItemSummary.from(it, bookTitlesById.getValue(it.bookId)) },
            pagination = Pagination.from(page),
        )
    }

    @Schema(name = "BookItemSearchSummary")
    data class BookItemSummary(
        @field:Schema(description = "소장본 ID", example = "1")
        val bookItemId: Long,
        @field:Schema(description = "도서 ID", example = "1")
        val bookId: Long,
        @field:Schema(description = "도서명", example = "클린 코드")
        val bookTitle: String,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "소장본 상태", example = "AVAILABLE")
        val status: BookItemStatus,
        @field:Schema(description = "등록일시")
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(bookItem: BookItem, bookTitle: String): BookItemSummary = BookItemSummary(
                bookItemId = bookItem.id,
                bookId = bookItem.bookId,
                bookTitle = bookTitle,
                managementNumber = bookItem.managementNumber,
                status = bookItem.status,
                createdAt = bookItem.createdAt,
            )
        }
    }

    @Schema(name = "BookItemSearchResponse", description = "소장본 검색·목록 응답")
    data class Response(
        val bookItems: List<BookItemSummary>,
        val pagination: Pagination,
    )
}
