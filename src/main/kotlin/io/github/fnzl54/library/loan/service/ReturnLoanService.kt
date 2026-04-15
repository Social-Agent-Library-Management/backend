package io.github.fnzl54.library.loan.service

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.core.application.BaseRequest
import io.github.fnzl54.library.core.application.BaseResponse
import io.github.fnzl54.library.core.application.BaseService
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.domain.repository.LoanRepository
import io.github.fnzl54.library.core.exception.DomainException
import io.github.fnzl54.library.core.exception.error.BaseErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class ReturnLoanService(
    private val loanRepository: LoanRepository,
) : BaseService<ReturnLoanService.Request, ReturnLoanService.Response>() {
    override fun doExecute(request: Request): Response {
        val loan =
            loanRepository.findByIdAndDeletedFalse(request.loanId)
                ?: throw ErrorCode.LOAN_NOT_FOUND.toException()

        if (loan.isReturned()) {
            throw ErrorCode.LOAN_ALREADY_RETURNED.toException()
        }

        loan.returnBook(LocalDate.now())

        return Response(loan)
    }

    enum class ErrorCode(
        override val message: String,
        override val httpStatus: HttpStatus,
    ) : BaseErrorCode<DomainException> {
        LOAN_NOT_FOUND("해당 대출을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
        LOAN_ALREADY_RETURNED("이미 반납된 대출입니다.", HttpStatus.CONFLICT),
        ;

        override fun toException(): DomainException = DomainException.from(this)
    }

    data class Request(
        val loanId: Long,
    ) : BaseRequest {
        @get:JsonIgnore
        override val isValid: Boolean
            get() = loanId > 0
    }

    class Response(loan: Loan) : BaseResponse<Loan>(result = loan)
}
