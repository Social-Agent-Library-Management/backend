package org.library.loan.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.library.core.application.getOrThrow
import org.library.core.presentation.PageRequestParams
import org.library.core.swagger.ApiErrorCode
import org.library.loan.application.CreateLoanService
import org.library.loan.application.OverdueLoansService
import org.library.loan.application.ReturnLoanService
import org.library.loan.application.SearchLoansService
import org.library.loan.domain.LoanStatus
import org.library.loan.domain.error.LoanError
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Loan", description = "대출 관리 API")
@RestController
@RequestMapping("/loans")
class LoanController(
    private val createLoanService: CreateLoanService,
    private val returnLoanService: ReturnLoanService,
    private val searchLoansService: SearchLoansService,
    private val overdueLoansService: OverdueLoansService,
) {

    @Operation(
        summary = "대출 등록",
        description = "관리번호로 소장본을 조회해 대출을 등록한다. 반납 예정일은 대여일 + 14일로 자동 계산되며, 등록 성공 시 소장본 상태가 ON_LOAN으로 바뀐다.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "대출 등록 성공",
        content = [Content(schema = Schema(implementation = CreateLoanService.Response::class))],
    )
    @ApiErrorCode(errorCodes = [LoanError::class], only = ["BOOK_ITEM_NOT_FOUND", "BOOK_ITEM_NOT_AVAILABLE"])
    @PostMapping
    fun create(@Valid @RequestBody request: CreateLoanService.Request): ResponseEntity<CreateLoanService.Response> {
        val loan = createLoanService.execute(request).getOrThrow()
        return ResponseEntity.status(HttpStatus.CREATED).body(loan)
    }

    @Operation(
        summary = "반납 처리",
        description = "대출 건을 반납 처리한다. 실제 반납일을 지정하지 않으면 오늘 날짜로 기록되며, 소장본 상태가 AVAILABLE로 돌아간다.",
    )
    @ApiErrorCode(errorCodes = [LoanError::class], only = ["LOAN_NOT_FOUND", "LOAN_NOT_ON_LOAN"])
    @PostMapping("/{loanId}/return")
    fun returnLoan(
        @Parameter(description = "대출 ID") @PathVariable loanId: Long,
        @RequestBody(required = false) request: ReturnLoanService.Request?,
    ): ReturnLoanService.Response =
        returnLoanService.execute(loanId, request ?: ReturnLoanService.Request()).getOrThrow()

    @Operation(
        summary = "대출 내역 검색·목록",
        description = "도서명·대출자 이름·부서·상태로 대출 내역을 검색한다. 기본 정렬은 대여일 내림차순.",
    )
    @GetMapping
    fun search(
        @Parameter(description = "도서명 부분 일치") @RequestParam(required = false) bookTitle: String?,
        @Parameter(description = "대출자 이름 부분 일치") @RequestParam(required = false) borrowerName: String?,
        @Parameter(description = "부서명 부분 일치") @RequestParam(required = false) department: String?,
        @Parameter(description = "대출 상태") @RequestParam(required = false) status: LoanStatus?,
        @ParameterObject params: PageRequestParams,
    ): SearchLoansService.Response =
        searchLoansService.execute(bookTitle, borrowerName, department, status, params)

    @Operation(
        summary = "연체 목록",
        description = "대출 중이며 반납 예정일이 지난 대출 건을 연체 경과일 내림차순으로 조회한다. " +
            "연체 여부·경과일은 저장하지 않고 조회 시점 날짜 기준으로 계산한다. 유예 기간 없이 반납 예정일 익일부터 연체로 집계된다.",
    )
    @GetMapping("/overdue")
    fun overdue(
        @Parameter(description = "부서명 부분 일치") @RequestParam(required = false) department: String?,
        @ParameterObject params: PageRequestParams,
    ): OverdueLoansService.Response =
        overdueLoansService.execute(department, params)
}
