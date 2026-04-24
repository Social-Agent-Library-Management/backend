package io.github.fnzl54.library.core.domain.entity

import io.github.fnzl54.library.core.domain.share.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "loan")
class Loan(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_item_id", nullable = false)
    val bookItem: BookItem,
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val department: String,
    val email: String? = null,
    @Column(nullable = false)
    val loanDate: LocalDate,
    @Column(nullable = false)
    val dueDate: LocalDate,
    @Column
    var returnDate: LocalDate? = null,
) : BaseEntity() {
    enum class LoanStatus {
        BORROWED,
        OVERDUE,
        RETURNED,
    }

    fun isReturned(): Boolean = returnDate != null

    fun computeLoanStatus(today: LocalDate = LocalDate.now()): LoanStatus =
        when {
            returnDate != null -> LoanStatus.RETURNED
            dueDate < today -> LoanStatus.OVERDUE
            else -> LoanStatus.BORROWED
        }

    fun returnBook(returnDate: LocalDate) {
        this.returnDate = returnDate
        this.bookItem.status = BookItem.Status.AVAILABLE
    }
}
