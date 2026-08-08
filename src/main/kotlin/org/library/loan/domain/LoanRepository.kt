package org.library.loan.domain

import org.springframework.data.jpa.repository.JpaRepository

interface LoanRepository : JpaRepository<Loan, Long> {

}
