package org.library.book.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.library.book.domain.Book
import java.time.LocalDateTime

@Schema(description = "도서 응답")
data class BookResponse(
    @field:Schema(description = "도서 ID", example = "1")
    val id: Long,
    @field:Schema(description = "도서 제목", example = "클린 아키텍처")
    val title: String,
    @field:Schema(description = "저자", example = "로버트 마틴")
    val author: String,
    @field:Schema(description = "ISBN (미입력 가능)", example = "9788966262472", nullable = true)
    val isbn: String?,
    @field:Schema(description = "등록 일시")
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(book: Book): BookResponse = BookResponse(
            id = book.id,
            title = book.title,
            author = book.author,
            isbn = book.isbn,
            createdAt = book.createdAt,
        )
    }
}
