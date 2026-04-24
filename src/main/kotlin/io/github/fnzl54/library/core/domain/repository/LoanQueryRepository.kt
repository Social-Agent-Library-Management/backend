package io.github.fnzl54.library.core.domain.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.domain.entity.QBook.book
import io.github.fnzl54.library.core.domain.entity.QBookItem.bookItem
import io.github.fnzl54.library.core.domain.entity.QLoan.loan
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class LoanQueryRepository(
    private val jpaQueryFactory: JPAQueryFactory,
) {
    data class LoanSearchRequest(
        val title: String?,
        val borrowerName: String?,
        val callNumber: String?,
        val status: Loan.LoanStatus?,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val today: LocalDate,
    )

    fun searchLoans(
        loanSearchRequest: LoanSearchRequest,
        pageable: Pageable,
    ): Page<Loan> {
        val predicates =
            arrayOf(
                loan.deleted.isFalse,
                titleContains(loanSearchRequest.title),
                borrowerNameContains(loanSearchRequest.borrowerName),
                callNumberContains(loanSearchRequest.callNumber),
                loanDateGoe(loanSearchRequest.startDate),
                loanDateLoe(loanSearchRequest.endDate),
                statusEquals(loanSearchRequest.status, loanSearchRequest.today),
            )

        val content =
            jpaQueryFactory
                .selectFrom(loan)
                .innerJoin(loan.bookItem, bookItem).fetchJoin()
                .innerJoin(bookItem.book, book).fetchJoin()
                .where(*predicates)
                .orderBy(loan.loanDate.desc(), loan.id.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        val countQuery =
            jpaQueryFactory
                .select(loan.count())
                .from(loan)
                .innerJoin(loan.bookItem, bookItem)
                .innerJoin(bookItem.book, book)
                .where(*predicates)

        return PageableExecutionUtils.getPage(content, pageable) { countQuery.fetchOne() ?: 0L }
    }

    private fun titleContains(title: String?): BooleanExpression? =
        title?.takeIf { it.isNotBlank() }?.let { book.title.containsIgnoreCase(it) }

    private fun borrowerNameContains(name: String?): BooleanExpression? =
        name?.takeIf { it.isNotBlank() }?.let { loan.name.containsIgnoreCase(it) }

    private fun callNumberContains(callNumber: String?): BooleanExpression? =
        callNumber?.takeIf { it.isNotBlank() }?.let { bookItem.callNumber.containsIgnoreCase(it) }

    private fun loanDateGoe(startDate: LocalDate?): BooleanExpression? = startDate?.let { loan.loanDate.goe(it) }

    private fun loanDateLoe(endDate: LocalDate?): BooleanExpression? = endDate?.let { loan.loanDate.loe(it) }

    private fun statusEquals(
        status: Loan.LoanStatus?,
        today: LocalDate,
    ): BooleanExpression? =
        when (status) {
            null -> null
            Loan.LoanStatus.RETURNED -> loan.returnDate.isNotNull
            Loan.LoanStatus.BORROWED -> loan.returnDate.isNull.and(loan.dueDate.goe(today))
            Loan.LoanStatus.OVERDUE -> loan.returnDate.isNull.and(loan.dueDate.lt(today))
        }
}
