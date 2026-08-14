package org.library.book.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.book.domain.Book
import org.library.book.domain.BookRepository
import org.library.book.domain.error.BookError
import org.library.bookitem.domain.BookItem
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class GetBookService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) {

    fun execute(id: Long): Result<Response, BookError> {
        val book = bookRepository.findByIdAndDeletedAtIsNull(id)
            ?: return BookError.NOT_FOUND.err()
        val bookItems = bookItemRepository.findAllByBookIdOrderByCreatedAtAsc(id)
        return Response.from(book, bookItems).ok()
    }

    @Schema(name = "GetBookResponse")
    data class Response(
        @field:Schema(description = "도서 ID", example = "1")
        val id: Long,
        @field:Schema(description = "도서 제목", example = "클린 아키텍처")
        val title: String,
        @field:Schema(description = "저자", example = "로버트 마틴")
        val author: String,
        @field:Schema(description = "출판사", example = "인사이트")
        val publisher: String,
        @field:Schema(description = "ISBN (미입력 가능)", example = "9788966262472", nullable = true)
        val isbn: String?,
        @field:Schema(description = "등록 일시")
        val createdAt: LocalDateTime,
        @field:Schema(description = "소장본 목록")
        val bookItems: List<BookItemSummary>,
    ) {
        companion object {
            fun from(book: Book, bookItems: List<BookItem>): Response = Response(
                id = book.id,
                title = book.title,
                author = book.author,
                publisher = book.publisher,
                isbn = book.isbn,
                createdAt = book.createdAt,
                bookItems = bookItems.map { BookItemSummary.from(it) },
            )
        }
    }

    @Schema(name = "BookItemSummary")
    data class BookItemSummary(
        @field:Schema(description = "소장본 ID", example = "10")
        val bookItemId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "상태", example = "AVAILABLE")
        val status: BookItemStatus,
        @field:Schema(description = "등록 일시")
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(bookItem: BookItem): BookItemSummary = BookItemSummary(
                bookItemId = bookItem.id,
                managementNumber = bookItem.managementNumber,
                status = bookItem.status,
                createdAt = bookItem.createdAt,
            )
        }
    }
}
