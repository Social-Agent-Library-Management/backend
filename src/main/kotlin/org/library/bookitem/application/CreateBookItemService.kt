package org.library.bookitem.application

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import org.library.book.domain.BookRepository
import org.library.bookitem.domain.BookItem
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
import org.library.bookitem.domain.error.BookItemError
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CreateBookItemService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) {

    @Transactional
    fun execute(bookId: Long, request: Request): Result<Response, BookItemError> {
        bookRepository.findByIdAndDeletedAtIsNull(bookId)
            ?: return BookItemError.BOOK_NOT_FOUND.err()

        val managementNumber = request.managementNumber.trim()
        if (!BookItem.MANAGEMENT_NUMBER_REGEX.matches(managementNumber)) {
            return BookItemError.INVALID_MANAGEMENT_NUMBER_FORMAT.err()
        }
        if (bookItemRepository.findByManagementNumberAndDeletedAtIsNull(managementNumber) != null) {
            return BookItemError.DUPLICATE_MANAGEMENT_NUMBER.err()
        }

        val bookItem = bookItemRepository.save(BookItem(bookId = bookId, managementNumber = managementNumber))
        return Response.from(bookItem).ok()
    }

    @Schema(name = "CreateBookItemRequest")
    data class Request(
        @field:NotBlank
        @field:Schema(description = "관리번호. (주제)-(번호) 형식, 전체 고유", example = "기술-0001")
        val managementNumber: String,
    )

    @Schema(name = "CreateBookItemResponse")
    data class Response(
        @field:Schema(description = "소장본 ID", example = "10")
        val bookItemId: Long,
        @field:Schema(description = "도서 ID", example = "1")
        val bookId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "상태", example = "AVAILABLE")
        val status: BookItemStatus,
        @field:Schema(description = "등록 일시")
        val createdAt: LocalDateTime,
    ) {
        companion object {
            fun from(bookItem: BookItem): Response = Response(
                bookItemId = bookItem.id,
                bookId = bookItem.bookId,
                managementNumber = bookItem.managementNumber,
                status = bookItem.status,
                createdAt = bookItem.createdAt,
            )
        }
    }
}
