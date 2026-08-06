package org.library.book.application

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import org.library.book.domain.Book
import org.library.book.domain.BookRepository
import org.library.book.domain.error.BookError
import org.library.book.dto.BookResponse
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateBookService(
    private val bookRepository: BookRepository,
) {

    @Transactional
    fun execute(id: Long, request: Request): Result<BookResponse, BookError> {
        val book = bookRepository.findByIdAndDeletedAtIsNull(id)
            ?: return BookError.NOT_FOUND.err()

        val isbn = Book.normalizeIsbn(request.isbn)
        if (isbn != null) {
            val owner = bookRepository.findByIsbnAndDeletedAtIsNull(isbn)
            if (owner != null && owner.id != id) {
                return BookError.DUPLICATE_ISBN.err()
            }
        }

        book.update(title = request.title, author = request.author, isbn = isbn)
        return BookResponse.from(book).ok()
    }

    @Schema(name = "UpdateBookRequest")
    data class Request(
        @field:NotBlank
        @field:Schema(description = "도서 제목", example = "클린 아키텍처")
        val title: String,
        @field:NotBlank
        @field:Schema(description = "저자", example = "로버트 마틴")
        val author: String,
        @field:Schema(description = "ISBN (미입력 시 기존 값이 지워진다)", example = "9788966262472", nullable = true)
        val isbn: String? = null,
    )
}
