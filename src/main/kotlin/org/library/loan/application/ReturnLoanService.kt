package org.library.loan.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.bookitem.domain.BookItemRepository
import org.library.core.application.Result
import org.library.core.application.err
import org.library.core.application.ok
import org.library.loan.domain.Loan
import org.library.loan.domain.LoanRepository
import org.library.loan.domain.LoanStatus
import org.library.loan.domain.error.LoanError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ReturnLoanService(
    private val loanRepository: LoanRepository,
    private val bookItemRepository: BookItemRepository,
) {

    @Transactional
    fun execute(loanId: Long, request: Request): Result<Response, LoanError> {
        val loan = loanRepository.findByIdForUpdate(loanId)
            ?: return LoanError.LOAN_NOT_FOUND.err()

        if (loan.status != LoanStatus.ON_LOAN) {
            return LoanError.LOAN_NOT_ON_LOAN.err()
        }

        val bookItem = bookItemRepository.findById(loan.bookItemId).orElse(null)
            ?: error("Loan(${loan.id})이 가리키는 소장본을 찾을 수 없습니다.")

        loan.markReturned(request.returnedAt ?: LocalDate.now())
        bookItem.markAvailable()

        return Response.from(loan).ok()
    }

    @Schema(name = "ReturnLoanRequest")
    data class Request(
        @field:Schema(description = "실제 반납일 (미지정 시 오늘 날짜)", example = "2026-08-20", nullable = true)
        val returnedAt: LocalDate? = null,
    )

    @Schema(name = "ReturnLoanResponse")
    data class Response(
        @field:Schema(description = "대출 ID", example = "100")
        val loanId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "대여일", example = "2026-07-31")
        val loanDate: LocalDate,
        @field:Schema(description = "반납 예정일", example = "2026-08-14")
        val dueDate: LocalDate,
        @field:Schema(description = "실제 반납일", example = "2026-08-20")
        val returnedAt: LocalDate?,
        @field:Schema(description = "대출 상태", example = "RETURNED")
        val status: LoanStatus,
        @field:Schema(description = "반납 지연 일수 (지연 없으면 null)", example = "6", nullable = true)
        val overdueDays: Long?,
    ) {
        companion object {
            fun from(loan: Loan): Response = Response(
                loanId = loan.id,
                managementNumber = loan.managementNumber,
                loanDate = loan.loanDate,
                dueDate = loan.dueDate,
                returnedAt = loan.returnedAt,
                status = loan.status,
                overdueDays = loan.overdueDays(),
            )
        }
    }
}
