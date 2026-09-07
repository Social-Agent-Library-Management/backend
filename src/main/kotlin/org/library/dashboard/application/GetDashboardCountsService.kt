package org.library.dashboard.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.book.domain.repository.BookRepository
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.BookItemStatus
import org.library.core.config.CacheConfig.CacheNames
import org.library.loan.domain.LoanRepository
import org.library.loan.domain.LoanStatus
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class GetDashboardCountsService(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
    private val loanRepository: LoanRepository,
) {

    @Cacheable(cacheNames = [CacheNames.DASHBOARD_SUMMARY])
    fun execute(): Response {
        val today = LocalDate.now()
        return Response(
            bookCount = bookRepository.countByDeletedAtIsNull(),
            bookItemCount = bookItemRepository.countByDeletedAtIsNull(),
            availableBookItemCount = bookItemRepository.countByDeletedAtIsNullAndStatus(BookItemStatus.AVAILABLE),
            activeLoanCount = loanRepository.countByStatus(LoanStatus.ON_LOAN),
            overdueLoanCount = loanRepository.countOverdue(LoanStatus.ON_LOAN, today),
        )
    }

    @Schema(name = "DashboardCountsResponse", description = "대시보드 집계 카운트 (개인정보를 포함하지 않아 캐싱 대상)")
    data class Response(
        @field:Schema(description = "전체 도서 수", example = "1200")
        val bookCount: Long,
        @field:Schema(description = "전체 소장본 수", example = "1450")
        val bookItemCount: Long,
        @field:Schema(description = "대출 가능 소장본 수", example = "980")
        val availableBookItemCount: Long,
        @field:Schema(description = "현재 대출 중인 건수 (연체 포함)", example = "42")
        val activeLoanCount: Long,
        @field:Schema(description = "연체 건수", example = "5")
        val overdueLoanCount: Long,
    )
}
