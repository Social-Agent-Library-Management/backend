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
class CreateBookService(
    private val bookRepository: BookRepository,
) {

    @Transactional
    fun execute(request: Request): Result<BookResponse, BookError> {
        val isbn = Book.normalizeIsbn(request.isbn)
        if (isbn != null && bookRepository.findByIsbnAndDeletedAtIsNull(isbn) != null) {
            return BookError.DUPLICATE_ISBN.err()
        }
        val book = bookRepository.save(
            Book(title = request.title, author = request.author, isbn = isbn),
        )
        return BookResponse.from(book).ok()
    }

    @Schema(name = "CreateBookRequest")
    data class Request(
        @field:NotBlank
        @field:Schema(description = "도서 제목", example = "클린 아키텍처")
        val title: String,
        @field:NotBlank
        @field:Schema(description = "저자", example = "로버트 마틴")
        val author: String,
        @field:Schema(description = "ISBN (미입력 가능)", example = "9788966262472", nullable = true)
        val isbn: String? = null,
    )
}
