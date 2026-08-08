package org.library.loan.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.library.core.domain.BaseEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "loans")
class Loan(
    bookItemId: Long,
    managementNumber: String,
    bookTitle: String,
    borrowerName: String,
    department: String,
    borrowerEmail: String?,
    loanDate: LocalDate,
) : BaseEntity() {

    @Column(nullable = false)
    var bookItemId: Long = bookItemId
        protected set

    @Column(nullable = false)
    var managementNumber: String = managementNumber
        protected set

    @Column(nullable = false)
    var bookTitle: String = bookTitle
        protected set

    @Column(nullable = false)
    var borrowerName: String = borrowerName
        protected set

    @Column(nullable = false)
    var department: String = department
        protected set

    @Column
    var borrowerEmail: String? = borrowerEmail
        protected set

    @Column(nullable = false)
    var loanDate: LocalDate = loanDate
        protected set

    @Column(nullable = false)
    var dueDate: LocalDate = loanDate.plusDays(LOAN_PERIOD_DAYS)
        protected set

    @Column
    var returnedAt: LocalDate? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: LoanStatus = LoanStatus.ON_LOAN
        protected set

    init {
        require(borrowerName.isNotBlank()) { "대출자 이름은 비어 있을 수 없습니다." }
        require(department.isNotBlank()) { "부서명은 비어 있을 수 없습니다." }
    }

    fun markReturned(returnedAt: LocalDate) {
        require(status == LoanStatus.ON_LOAN) { "이미 반납된 대출 건입니다." }
        this.returnedAt = returnedAt
        this.status = LoanStatus.RETURNED
    }

    fun isOverdue(today: LocalDate): Boolean = status == LoanStatus.ON_LOAN && dueDate < today

    fun overdueDays(): Long? {
        val returned = returnedAt ?: return null
        return ChronoUnit.DAYS.between(dueDate, returned).takeIf { it > 0 }
    }

    companion object {

        const val LOAN_PERIOD_DAYS = 14L
    }
}
