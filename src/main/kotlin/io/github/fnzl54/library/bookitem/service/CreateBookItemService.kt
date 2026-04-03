package io.github.fnzl54.library.bookitem.service

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.core.application.BaseRequest
import io.github.fnzl54.library.core.application.BaseResponse
import io.github.fnzl54.library.core.application.BaseService
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.repository.BookItemRepository
import io.github.fnzl54.library.core.domain.repository.BookRepository
import io.github.fnzl54.library.core.exception.DomainException
import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CreateBookItemService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) : BaseService<CreateBookItemService.Request, CreateBookItemService.Response>() {
    override fun doExecute(request: Request): Response {
        val book =
            bookRepository.findByIdOrNull(request.bookId)
                ?.takeIf { !it.deleted }
                ?: throw ErrorCode.BOOK_NOT_FOUND.toException()

        if (bookItemRepository.existsByCallNumber(request.callNumber)) {
            throw ErrorCode.DUPLICATE_CALL_NUMBER.toException()
        }

        val bookItem =
            BookItem(
                book = book,
                callNumber = request.callNumber,
            )

        val savedBookItem = bookItemRepository.save(bookItem)

        return Response(savedBookItem)
    }

    enum class ErrorCode(
        override val message: String,
        override val httpStatus: HttpStatus,
    ) : BaseErrorCode<DomainException> {
        BOOK_NOT_FOUND("해당 도서를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
        DUPLICATE_CALL_NUMBER("이미 등록된 관리번호입니다.", HttpStatus.CONFLICT),
        ;

        override fun toException(): DomainException = DomainException.from(this)
    }

    data class Request(
        val bookId: Long,
        val callNumber: String,
    ) : BaseRequest {
        @get:JsonIgnore
        override val isValid: Boolean
            get() = bookId > 0 && callNumber.isNotBlank()
    }

    class Response(bookItem: BookItem) : BaseResponse<BookItem>(result = bookItem)
}
