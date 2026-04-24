package io.github.fnzl54.library.loan.service

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.core.application.BaseRequest
import io.github.fnzl54.library.core.application.BaseResponse
import io.github.fnzl54.library.core.application.BaseService
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.domain.repository.LoanQueryRepository
import io.github.fnzl54.library.core.presentation.pagination.Pagination
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ReadLoanListService(
    private val loanQueryRepository: LoanQueryRepository,
) : BaseService<ReadLoanListService.Request, ReadLoanListService.Response>() {
    override fun doExecute(request: Request): Response {
        val today = LocalDate.now()

        val condition =
            LoanQueryRepository.LoanSearchRequest(
                title = request.title,
                borrowerName = request.borrowerName,
                callNumber = request.callNumber,
                status = request.status,
                startDate = request.startDate,
                endDate = request.endDate,
                today = today,
            )

        val loanPage = loanQueryRepository.searchLoans(condition, request.pageable)

        val loans =
            loanPage.content.map { loan ->
                LoanSummary(
                    loanId = requireNotNull(loan.id) { "대출 엔티티 ID가 null입니다: $loan" },
                    bookTitle = loan.bookItem.book.title,
                    author = loan.bookItem.book.author,
                    callNumber = loan.bookItem.callNumber,
                    borrowerName = loan.name,
                    department = loan.department,
                    loanDate = loan.loanDate,
                    dueDate = loan.dueDate,
                    returnDate = loan.returnDate,
                    loanStatus = loan.computeLoanStatus(today),
                )
            }

        return Response(
            PageResult(
                pagination = Pagination.from(loanPage),
                loans = loans,
            ),
        )
    }

    data class Request(
        val title: String?,
        val borrowerName: String?,
        val callNumber: String?,
        val status: Loan.LoanStatus?,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val pageable: Pageable,
    ) : BaseRequest {
        @get:JsonIgnore
        override val isValid: Boolean
            get() = startDate == null || endDate == null || !startDate.isAfter(endDate)
    }

    data class PageResult(
        val pagination: Pagination,
        val loans: List<LoanSummary>,
    )

    data class LoanSummary(
        val loanId: Long,
        val bookTitle: String,
        val author: String,
        val callNumber: String,
        val borrowerName: String,
        val department: String,
        val loanDate: LocalDate,
        val dueDate: LocalDate,
        val returnDate: LocalDate?,
        val loanStatus: Loan.LoanStatus,
    )

    class Response(pageResult: PageResult) : BaseResponse<PageResult>(result = pageResult)
}
