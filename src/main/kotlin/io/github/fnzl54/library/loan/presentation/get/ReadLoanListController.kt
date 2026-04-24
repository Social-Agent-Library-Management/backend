package io.github.fnzl54.library.loan.presentation.get

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.github.fnzl54.library.core.presentation.pagination.Pagination
import io.github.fnzl54.library.core.presentation.pagination.PaginationRequest
import io.github.fnzl54.library.loan.service.ReadLoanListService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import org.springdoc.core.annotations.ParameterObject
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@Tag(name = "Loan", description = "대출")
class ReadLoanListController(
    private val readLoanListService: ReadLoanListService,
) {
    @Operation(
        summary = "대출 내역 조회 API",
        description =
            "조건에 맞는 대출 내역을 페이지 단위로 조회합니다.\n\n" +
                "모든 검색 조건은 선택사항이며, 조건을 입력하지 않으면 전체 대출 내역을 조회합니다.\n\n" +
                "상태(status)는 BORROWED(대출중), OVERDUE(연체), RETURNED(반납완료) 중 하나입니다.\n\n" +
                "startDate, endDate는 대출일 기준 범위를 설정합니다.",
        operationId = "readLoanList",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = ReadLoanListResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class])
    @GetMapping("/loans")
    fun readLoanList(
        @Valid @ParameterObject request: ReadLoanListRequest,
    ): ResponseEntity<ReadLoanListResponse> {
        val serviceRequest =
            ReadLoanListService.Request(
                title = request.title,
                borrowerName = request.borrowerName,
                callNumber = request.callNumber,
                status = request.status,
                startDate = request.startDate,
                endDate = request.endDate,
                pageable = request.toPageRequest(),
            )

        val serviceResponse = readLoanListService.execute(serviceRequest)
        return ResponseEntity.ok(toResponse(serviceResponse.result))
    }

    private fun toResponse(pageResult: ReadLoanListService.PageResult): ReadLoanListResponse =
        ReadLoanListResponse(
            statusCode = HttpStatus.OK.value(),
            result =
                ReadLoanListResponse.Result(
                    pagination = pageResult.pagination,
                    loans =
                        pageResult.loans.map { summary ->
                            ReadLoanListResponse.LoanItem(
                                loanId = summary.loanId,
                                bookTitle = summary.bookTitle,
                                author = summary.author,
                                callNumber = summary.callNumber,
                                borrowerName = summary.borrowerName,
                                department = summary.department,
                                loanDate = summary.loanDate,
                                dueDate = summary.dueDate,
                                returnDate = summary.returnDate,
                                status = summary.loanStatus,
                            )
                        },
                ),
        )

    data class ReadLoanListRequest(
        @Schema(description = "도서명 (부분 검색)", example = "사회복지")
        val title: String? = null,
        @Schema(description = "대출자 이름 (부분 검색)", example = "박영희")
        val borrowerName: String? = null,
        @Schema(description = "관리번호 (부분 검색)", example = "LIB-001")
        val callNumber: String? = null,
        @Schema(description = "상태 (BORROWED: 대출중, OVERDUE: 연체, RETURNED: 반납완료)")
        val status: Loan.LoanStatus? = null,
        @Schema(description = "대출일 시작 (yyyy-MM-dd)", example = "2026-01-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        val startDate: LocalDate? = null,
        @Schema(description = "대출일 종료 (yyyy-MM-dd)", example = "2026-04-19")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        val endDate: LocalDate? = null,
        @Schema(description = "페이지 번호 (1부터 시작, 기본값: 1)", example = "1")
        override val page: Int = 1,
        @Schema(description = "페이지 크기 (기본값: 10, 최대: 100)", example = "10")
        override val size: Int = 10,
    ) : PaginationRequest(page, size) {
        @get:JsonIgnore
        @get:AssertTrue(message = "시작일은 종료일보다 이전이어야 합니다.")
        val isDateRangeValid: Boolean
            get() = startDate == null || endDate == null || !startDate.isAfter(endDate)
    }

    class ReadLoanListResponse(
        @Schema(description = "HTTP 상태 코드", example = "200")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<ReadLoanListResponse.Result>(statusCode = statusCode, result = result) {
        data class Result(
            val pagination: Pagination,
            val loans: List<LoanItem>,
        )

        data class LoanItem(
            @Schema(description = "대출 ID", example = "1")
            val loanId: Long,
            @Schema(description = "도서명", example = "사회복지학 개론")
            val bookTitle: String,
            @Schema(description = "저자", example = "김철수")
            val author: String,
            @Schema(description = "관리번호", example = "LIB-001")
            val callNumber: String,
            @Schema(description = "대출자 이름", example = "박영희")
            val borrowerName: String,
            @Schema(description = "부서", example = "사회복지과")
            val department: String,
            @Schema(description = "대출일", example = "2026-03-07")
            val loanDate: LocalDate,
            @Schema(description = "반납 예정일", example = "2026-03-21")
            val dueDate: LocalDate,
            @Schema(description = "실제 반납일 (미반납 시 null)", example = "null")
            val returnDate: LocalDate?,
            @Schema(description = "대출 상태 (BORROWED: 대출중, OVERDUE: 연체, RETURNED: 반납완료)", example = "BORROWED")
            val status: Loan.LoanStatus,
        )
    }
}
