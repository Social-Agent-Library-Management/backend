package org.library.loan.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.core.presentation.PageRequestParams
import org.library.loan.domain.Loan
import org.library.loan.domain.LoanRepository
import org.library.loan.domain.LoanStatus
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class RecentLoanActivitiesService(
    private val loanRepository: LoanRepository,
) {

    fun execute(params: PageRequestParams): List<LoanActivity> {
        val today = LocalDate.now()
        val pageRequest = params.toPageRequest(Sort.by(Sort.Direction.DESC, "updatedAt", "id"))
        return loanRepository.findAll(pageRequest).content.map { LoanActivity.from(it, today) }
    }

    @Schema(name = "LoanActivity", description = "대출·반납 활동 1건")
    data class LoanActivity(
        @field:Schema(description = "대출 ID", example = "100")
        val loanId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "도서명", example = "클린 코드")
        val bookTitle: String,
        @field:Schema(description = "대출자 이름 (반납 완료 건은 개인정보 파기로 null)", example = "홍길동", nullable = true)
        val borrowerName: String?,
        @field:Schema(description = "부서명", example = "총무과")
        val department: String,
        @field:Schema(description = "대여일", example = "2026-07-31")
        val loanDate: LocalDate,
        @field:Schema(description = "반납 예정일", example = "2026-08-14")
        val dueDate: LocalDate,
        @field:Schema(description = "실제 반납일", example = "2026-08-20", nullable = true)
        val returnedAt: LocalDate?,
        @field:Schema(description = "대출 상태 (ON_LOAN이면 대출 활동, RETURNED면 반납 활동)", example = "RETURNED")
        val status: LoanStatus,
        @field:Schema(description = "연체 여부 (대출 중이면서 반납 예정일이 지남)", example = "false")
        val overdue: Boolean,
        @field:Schema(description = "활동이 발생한 시각 (대출 또는 반납 시점, 이 값 기준으로 정렬된다)")
        val activityAt: LocalDateTime,
    ) {
        companion object {
            fun from(loan: Loan, today: LocalDate): LoanActivity = LoanActivity(
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
                activityAt = loan.updatedAt,
            )
        }
    }
}
