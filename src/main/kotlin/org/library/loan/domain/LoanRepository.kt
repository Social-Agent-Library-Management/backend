package org.library.loan.domain

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface LoanRepository : JpaRepository<Loan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Loan?

    @Query(
        """
        select l from Loan l
        where (:bookTitle is null or lower(l.bookTitle) like lower(concat('%', :bookTitle, '%')))
          and (:borrowerName is null or lower(l.borrowerName) like lower(concat('%', :borrowerName, '%')))
          and (:department is null or lower(l.department) like lower(concat('%', :department, '%')))
          and (:status is null or l.status = :status)
        """,
    )
    fun search(
        @Param("bookTitle") bookTitle: String?,
        @Param("borrowerName") borrowerName: String?,
        @Param("department") department: String?,
        @Param("status") status: LoanStatus?,
        pageable: Pageable,
    ): Page<Loan>

    @Query(
        """
        select l from Loan l
        where l.status = :status
          and l.dueDate < :today
          and (:department is null or lower(l.department) like lower(concat('%', :department, '%')))
        """,
    )
    fun findOverdue(
        @Param("status") status: LoanStatus,
        @Param("today") today: LocalDate,
        @Param("department") department: String?,
        pageable: Pageable,
    ): Page<Loan>
}
