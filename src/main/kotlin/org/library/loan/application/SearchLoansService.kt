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
class SearchLoansService(
    private val loanRepository: LoanRepository,
) {

    fun execute(
        bookTitle: String?,
        borrowerName: String?,
        department: String?,
        status: LoanSearchStatus?,
        params: PageRequestParams,
    ): Response {
        val pageRequest = params.toPageRequest(Sort.by(Sort.Direction.DESC, "loanDate"))
        val today = LocalDate.now()
        val page = loanRepository.search(
            bookTitle = bookTitle,
            borrowerName = borrowerName,
            department = department,
            status = status?.loanStatus,
            overdueOnly = status == LoanSearchStatus.OVERDUE,
            today = today,
            pageable = pageRequest,
        )
        return Response(
            loans = page.content.map { LoanSummary.from(it, today) },
            pagination = Pagination.from(page),
        )
    }

    @Schema(name = "LoanSearchStatus", description = "대출 검색 상태 필터")
    enum class LoanSearchStatus(val loanStatus: LoanStatus) {
        ON_LOAN(LoanStatus.ON_LOAN),
        OVERDUE(LoanStatus.ON_LOAN),
        RETURNED(LoanStatus.RETURNED),
    }

    @Schema(name = "LoanSummary")
    data class LoanSummary(
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
        @field:Schema(description = "대여일", example = "2026-07-31")
        val loanDate: LocalDate,
        @field:Schema(description = "반납 예정일", example = "2026-08-14")
        val dueDate: LocalDate,
        @field:Schema(description = "실제 반납일", example = "2026-08-20", nullable = true)
        val returnedAt: LocalDate?,
        @field:Schema(description = "대출 상태", example = "ON_LOAN")
        val status: LoanStatus,
        @field:Schema(description = "연체 여부 (대출 중이면서 반납 예정일이 지남)", example = "true")
        val overdue: Boolean,
    ) {
        companion object {
            fun from(loan: Loan, today: LocalDate): LoanSummary = LoanSummary(
                loanId = loan.id,
                managementNumber = loan.managementNumber,
                bookTitle = loan.bookTitle,
                borrowerName = loan.borrowerName,
                department = loan.department,
                loanDate = loan.loanDate,
                dueDate = loan.dueDate,
                returnedAt = loan.returnedAt,
                status = loan.status,
                overdue = loan.isOverdue(today),
            )
        }
    }

    @Schema(name = "LoanSearchResponse", description = "대출 내역 검색·목록 응답")
    data class Response(
        val loans: List<LoanSummary>,
        val pagination: Pagination,
    )
}
