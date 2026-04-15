package io.github.fnzl54.library.core.domain.repository

import io.github.fnzl54.library.core.domain.entity.Loan
import org.springframework.data.jpa.repository.JpaRepository

interface LoanRepository : JpaRepository<Loan, Long> {
    fun findByIdAndDeletedFalse(id: Long): Loan?
}
