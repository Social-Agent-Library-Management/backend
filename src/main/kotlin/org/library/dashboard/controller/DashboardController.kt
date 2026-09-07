package org.library.dashboard.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.library.dashboard.application.GetDashboardSummaryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Dashboard", description = "대시보드 API")
@RestController
@RequestMapping("/dashboard")
class DashboardController(
    private val getDashboardSummaryService: GetDashboardSummaryService,
) {

    @Operation(
        summary = "대시보드 요약 조회",
        description = "전체 도서·소장본·대출·연체 현황과 최근 대출 활동, 연체 목록을 한 번에 조회한다. " +
            "최근 대출 활동은 대여일 내림차순, 연체 목록은 경과일 내림차순으로 정렬된다.",
    )
    @GetMapping("/summary")
    fun summary(
        @Parameter(description = "최근 대출 활동 표시 건수") @RequestParam(required = false, defaultValue = "10") recentLoanLimit: Int,
        @Parameter(description = "연체 목록 표시 건수") @RequestParam(required = false, defaultValue = "10") overdueLimit: Int,
    ): GetDashboardSummaryService.Response =
        getDashboardSummaryService.execute(recentLoanLimit, overdueLimit)
}
