package org.library.bookitem.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.bookitem.domain.BookItem
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
import org.library.bookitem.domain.error.BookItemError
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangeBookItemStatusService(
    private val bookItemRepository: BookItemRepository,
) {

    @Transactional
    fun execute(bookItemId: Long, request: Request): Result<Response, BookItemError> {
        val bookItem = bookItemRepository.findById(bookItemId).orElse(null)
            ?: return BookItemError.BOOK_ITEM_NOT_FOUND.err()

        if (request.status != BookItemStatus.LOST && request.status != BookItemStatus.DISPOSED) {
            return BookItemError.INVALID_STATUS.err()
        }

        bookItem.changeStatus(request.status)
        return Response.from(bookItem).ok()
    }

    @Schema(name = "ChangeBookItemStatusRequest")
    data class Request(
        @field:Schema(description = "변경할 상태 (LOST 또는 DISPOSED)", example = "LOST")
        val status: BookItemStatus,
    )

    @Schema(name = "ChangeBookItemStatusResponse")
    data class Response(
        @field:Schema(description = "소장본 ID", example = "10")
        val bookItemId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "상태", example = "LOST")
        val status: BookItemStatus,
    ) {
        companion object {
            fun from(bookItem: BookItem): Response = Response(
                bookItemId = bookItem.id,
                managementNumber = bookItem.managementNumber,
                status = bookItem.status,
            )
        }
    }
}
