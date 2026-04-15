package io.github.fnzl54.library.loan.presentation.patch

import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.github.fnzl54.library.loan.service.ReturnLoanService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@Tag(name = "Loan", description = "대출")
class ReturnLoanController(
    private val returnLoanService: ReturnLoanService,
) {
    @Operation(
        summary = "대출 반납 API",
        description =
            "대출 ID를 기준으로 반납을 처리합니다.\n\n" +
                "해당 대출이 존재해야 하며, 아직 반납되지 않은 상태여야 합니다.\n\n" +
                "반납 처리 시 소장본 상태는 AVAILABLE로 변경되고, 반납일은 서버 시간으로 자동 기록됩니다.",
        operationId = "returnLoan",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = ReturnLoanResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class, ReturnLoanService.ErrorCode::class])
    @PatchMapping("/loans/{loanId}")
    fun returnLoan(
        @PathVariable loanId: Long,
    ): ResponseEntity<ReturnLoanResponse> {
        val serviceRequest = ReturnLoanService.Request(loanId = loanId)
        val serviceResponse = returnLoanService.execute(serviceRequest)

        return ResponseEntity.ok(toResponse(serviceResponse.result))
    }

    private fun toResponse(loan: Loan): ReturnLoanResponse =
        ReturnLoanResponse(
            statusCode = HttpStatus.OK.value(),
            result =
                ReturnLoanResponse.Result(
                    loanId = requireNotNull(loan.id) { "저장된 대출의 ID가 존재하지 않습니다." },
                    bookItemId = requireNotNull(loan.bookItem.id) { "대출에 연결된 소장본의 ID가 존재하지 않습니다." },
                    callNumber = loan.bookItem.callNumber,
                    name = loan.name,
                    department = loan.department,
                    email = loan.email,
                    loanDate = loan.loanDate,
                    dueDate = loan.dueDate,
                    returnDate = requireNotNull(loan.returnDate) { "반납일이 설정되지 않았습니다." },
                    status = loan.bookItem.status,
                ),
        )

    class ReturnLoanResponse(
        @Schema(description = "HTTP 상태 코드", example = "200")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<ReturnLoanResponse.Result>(statusCode = statusCode, result = result) {
        data class Result(
            @Schema(description = "대출 ID", example = "1")
            val loanId: Long,
            @Schema(description = "소장본 ID", example = "1")
            val bookItemId: Long,
            @Schema(description = "관리번호", example = "C001-001")
            val callNumber: String,
            @Schema(description = "대출자 이름", example = "홍길동")
            val name: String,
            @Schema(description = "부서명", example = "개발팀")
            val department: String,
            @Schema(description = "대출자 이메일", example = "hong@example.com")
            val email: String?,
            @Schema(description = "대여일", example = "2026-04-04")
            val loanDate: LocalDate,
            @Schema(description = "반납 예정일", example = "2026-04-18")
            val dueDate: LocalDate,
            @Schema(description = "실제 반납일", example = "2026-04-15")
            val returnDate: LocalDate,
            @Schema(description = "소장본 상태", example = "AVAILABLE")
            val status: BookItem.Status,
        )
    }
}
