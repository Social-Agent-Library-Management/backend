package io.github.fnzl54.library.loan.service

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.core.application.BaseRequest
import io.github.fnzl54.library.core.application.BaseResponse
import io.github.fnzl54.library.core.application.BaseService
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.domain.repository.BookItemRepository
import io.github.fnzl54.library.core.domain.repository.LoanRepository
import io.github.fnzl54.library.core.exception.DomainException
import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class CreateLoanService(
    private val bookItemRepository: BookItemRepository,
    private val loanRepository: LoanRepository,
) : BaseService<CreateLoanService.Request, CreateLoanService.Response>() {
    override fun doExecute(request: Request): Response {
        val bookItem =
            bookItemRepository.findByCallNumber(request.callNumber)
                ?: throw ErrorCode.BOOK_ITEM_NOT_FOUND.toException()

        if (bookItem.status != BookItem.Status.AVAILABLE) {
            throw ErrorCode.BOOK_ITEM_NOT_AVAILABLE.toException()
        }

        if (request.dueDate < request.loanDate) {
            throw ErrorCode.INVALID_DUE_DATE.toException()
        }

        val loan =
            Loan(
                bookItem = bookItem,
                name = request.name,
                department = request.department,
                email = request.email,
                loanDate = request.loanDate,
                dueDate = request.dueDate,
            )

        loanRepository.save(loan)
        bookItem.status = BookItem.Status.BORROWED

        return Response(loan)
    }

    enum class ErrorCode(
        override val message: String,
        override val httpStatus: HttpStatus,
    ) : BaseErrorCode<DomainException> {
        BOOK_ITEM_NOT_FOUND("해당 소장본을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
        BOOK_ITEM_NOT_AVAILABLE("이미 대출 중인 소장본입니다.", HttpStatus.CONFLICT),
        INVALID_DUE_DATE("반납 예정일은 대여일보다 이후여야 합니다.", HttpStatus.BAD_REQUEST),
        ;

        override fun toException(): DomainException = DomainException.from(this)
    }

    data class Request(
        val callNumber: String,
        val name: String,
        val department: String,
        val email: String? = null,
        val loanDate: LocalDate,
        val dueDate: LocalDate,
    ) : BaseRequest {
        @get:JsonIgnore
        override val isValid: Boolean
            get() = callNumber.isNotBlank() && name.isNotBlank() && department.isNotBlank()
    }

    class Response(loan: Loan) : BaseResponse<Loan>(result = loan)
}
