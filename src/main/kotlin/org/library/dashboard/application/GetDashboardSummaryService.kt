package org.library.dashboard.application

import io.swagger.v3.oas.annotations.media.Schema
import org.library.core.presentation.PageRequestParams
import org.library.loan.application.OverdueLoansService
import org.library.loan.application.SearchLoansService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GetDashboardSummaryService(
    private val getDashboardCountsService: GetDashboardCountsService,
    private val searchLoansService: SearchLoansService,
    private val overdueLoansService: OverdueLoansService,
) {

    fun execute(recentLoanLimit: Int, overdueLimit: Int): Response {
        val counts = getDashboardCountsService.execute()

        val recentLoans = searchLoansService.execute(
            bookTitle = null,
            borrowerName = null,
            department = null,
            status = null,
            params = PageRequestParams(page = 1, pageSize = recentLoanLimit),
        ).loans

        val overdueLoans = overdueLoansService.execute(
            department = null,
            params = PageRequestParams(page = 1, pageSize = overdueLimit),
        ).loans

        return Response(
            bookCount = counts.bookCount,
            bookItemCount = counts.bookItemCount,
            availableBookItemCount = counts.availableBookItemCount,
            activeLoanCount = counts.activeLoanCount,
            overdueLoanCount = counts.overdueLoanCount,
            recentLoans = recentLoans,
            overdueLoans = overdueLoans,
        )
    }

    @Schema(name = "DashboardSummaryResponse", description = "대시보드 요약 응답")
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
        @field:Schema(description = "최근 대출 활동 목록 (대여일 내림차순, 캐싱하지 않음)")
        val recentLoans: List<SearchLoansService.LoanSummary>,
        @field:Schema(description = "연체 목록 (경과일 내림차순, 캐싱하지 않음)")
        val overdueLoans: List<OverdueLoansService.OverdueLoanSummary>,
    )
}
