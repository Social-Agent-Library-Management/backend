package io.github.fnzl54.library.book.service

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.core.application.BaseRequest
import io.github.fnzl54.library.core.application.BaseResponse
import io.github.fnzl54.library.core.application.BaseService
import io.github.fnzl54.library.core.domain.entity.Book
import io.github.fnzl54.library.core.domain.repository.BookRepository
import io.github.fnzl54.library.core.exception.DomainException
import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CreateBookService(
    private val bookRepository: BookRepository,
) : BaseService<CreateBookService.Request, CreateBookService.Result>() {
    override fun doExecute(request: Request): Result {
        if (!request.isbn.isNullOrBlank()) {
            if (bookRepository.existsByIsbnAndDeletedFalse(request.isbn)) {
                throw ErrorCode.DUPLICATE_ISBN.toException()
            }
        } else if (bookRepository.existsByTitleAndDeletedFalse(request.title)) {
            throw ErrorCode.DUPLICATE_TITLE.toException()
        }

        val book =
            Book(
                isbn = request.isbn,
                title = request.title,
                author = request.author,
                publisher = request.publisher,
            )

        bookRepository.save(book)

        return Result(book)
    }

    enum class ErrorCode(
        override val message: String,
        override val httpStatus: HttpStatus,
    ) : BaseErrorCode<DomainException> {
        DUPLICATE_ISBN("이미 등록된 ISBN입니다.", HttpStatus.CONFLICT),
        DUPLICATE_TITLE("이미 등록된 도서 제목입니다.", HttpStatus.CONFLICT),
        ;

        override fun toException(): DomainException = DomainException.from(this)
    }

    data class Request(
        val title: String,
        val author: String,
        val isbn: String? = null,
        val publisher: String? = null,
    ) : BaseRequest {
        @get:JsonIgnore
        override val isValid: Boolean
            get() = title.isNotBlank() && author.isNotBlank()
    }

    class Result(book: Book) : BaseResponse<Book>(result = book)
}
