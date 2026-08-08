package org.library.loan.application

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.library.book.domain.BookRepository
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
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
class CreateLoanService(
    private val bookItemRepository: BookItemRepository,
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository,
) {

    @Transactional
    fun execute(request: Request): Result<Response, LoanError> {
        val bookItem = bookItemRepository.findByManagementNumberForUpdate(request.managementNumber)
            ?: return LoanError.BOOK_ITEM_NOT_FOUND.err()

        if (bookItem.status != BookItemStatus.AVAILABLE) {
            return LoanError.BOOK_ITEM_NOT_AVAILABLE.err()
        }

        val book = bookRepository.findByIdAndDeletedAtIsNull(bookItem.bookId)
            ?: error("BookItem(${bookItem.id})이 가리키는 도서를 찾을 수 없습니다.")

        bookItem.markOnLoan()

        val loan = loanRepository.save(
            Loan(
                bookItemId = bookItem.id,
                managementNumber = bookItem.managementNumber,
                bookTitle = book.title,
                borrowerName = request.borrowerName,
                department = request.department,
                borrowerEmail = request.borrowerEmail,
                loanDate = request.loanDate,
            ),
        )
        return Response.from(loan).ok()
    }

    @Schema(name = "CreateLoanRequest")
    data class Request(
        @field:NotBlank
        @field:Schema(description = "대출할 소장본 관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:NotBlank
        @field:Schema(description = "대출자 이름", example = "홍길동")
        val borrowerName: String,
        @field:NotBlank
        @field:Schema(description = "부서명", example = "총무과")
        val department: String,
        @field:NotNull
        @field:Schema(description = "대여일", example = "2026-07-31")
        val loanDate: LocalDate,
        @field:Schema(description = "대출자 이메일 (미입력 시 관리자 이메일로 알림 발송)", example = "hong@example.com", nullable = true)
        val borrowerEmail: String? = null,
    )

    @Schema(name = "CreateLoanResponse")
    data class Response(
        @field:Schema(description = "대출 ID", example = "100")
        val loanId: Long,
        @field:Schema(description = "소장본 ID", example = "10")
        val bookItemId: Long,
        @field:Schema(description = "관리번호", example = "기술-0001")
        val managementNumber: String,
        @field:Schema(description = "도서명", example = "클린 코드")
        val bookTitle: String,
        @field:Schema(description = "대출자 이름", example = "홍길동")
        val borrowerName: String,
        @field:Schema(description = "부서명", example = "총무과")
        val department: String,
        @field:Schema(description = "대출자 이메일", example = "hong@example.com", nullable = true)
        val borrowerEmail: String?,
        @field:Schema(description = "대여일", example = "2026-07-31")
        val loanDate: LocalDate,
        @field:Schema(description = "반납 예정일", example = "2026-08-14")
        val dueDate: LocalDate,
        @field:Schema(description = "대출 상태", example = "ON_LOAN")
        val status: LoanStatus,
    ) {
        companion object {
            fun from(loan: Loan): Response = Response(
                loanId = loan.id,
                bookItemId = loan.bookItemId,
                managementNumber = loan.managementNumber,
                bookTitle = loan.bookTitle,
                borrowerName = loan.borrowerName,
                department = loan.department,
                borrowerEmail = loan.borrowerEmail,
                loanDate = loan.loanDate,
                dueDate = loan.dueDate,
                status = loan.status,
            )
        }
    }
}
