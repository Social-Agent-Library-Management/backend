package org.library.loan.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.core.presentation.PageRequestParams
import org.library.core.presentation.Pagination
import org.library.loan.domain.Loan
import org.library.loan.domain.LoanRepository
import org.library.loan.domain.LoanStatus
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class OverdueLoansService(
    private val loanRepository: LoanRepository,
) {

    fun execute(department: String?, params: PageRequestParams): Response {
        val today = LocalDate.now()
        val pageRequest = params.toPageRequest(Sort.by(Sort.Direction.ASC, "dueDate", "id"))
        val page = loanRepository.findOverdue(LoanStatus.ON_LOAN, today, department, pageRequest)
        return Response(
            loans = page.content.map { OverdueLoanSummary.from(it, today) },
            pagination = Pagination.from(page),
        )
    }

    @Schema(name = "OverdueLoanSummary")
    data class OverdueLoanSummary(
        @field:Schema(description = "대출 ID", example = "100")
        val loanId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "도서명", example = "클린 코드")
        val bookTitle: String,
        @field:Schema(description = "대출자 이름", example = "홍길동")
        val borrowerName: String,
        @field:Schema(description = "부서명", example = "총무과")
        val department: String,
        @field:Schema(description = "대여일", example = "2026-06-01")
        val loanDate: LocalDate,
        @field:Schema(description = "반납 예정일", example = "2026-06-15")
        val dueDate: LocalDate,
        @field:Schema(description = "연체 경과일", example = "46")
        val overdueDays: Long,
    ) {
        companion object {
            fun from(loan: Loan, today: LocalDate): OverdueLoanSummary = OverdueLoanSummary(
                loanId = loan.id,
                managementNumber = loan.managementNumber,
                bookTitle = loan.bookTitle,
                borrowerName = loan.borrowerName,
                department = loan.department,
                loanDate = loan.loanDate,
                dueDate = loan.dueDate,
                overdueDays = loan.currentOverdueDays(today),
            )
        }
    }

    @Schema(name = "OverdueLoanListResponse", description = "연체 목록 응답")
    data class Response(
        val loans: List<OverdueLoanSummary>,
        val pagination: Pagination,
    )
}
