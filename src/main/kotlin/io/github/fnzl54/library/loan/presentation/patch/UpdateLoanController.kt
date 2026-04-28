package io.github.fnzl54.library.loan.presentation.patch

import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.github.fnzl54.library.loan.service.UpdateLoanService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@Tag(name = "Loan", description = "대출")
class UpdateLoanController(
    private val updateLoanService: UpdateLoanService,
) {
    @Operation(
        summary = "대출 정보 수정 API",
        description =
            "대출 ID를 기준으로 대출자 정보(이름/부서/이메일)를 수정합니다.\n\n" +
                "BORROWED 상태에서만 수정 가능하며, 반납 완료(RETURNED) 또는 연체(OVERDUE) 상태에서는 수정할 수 없습니다.\n\n" +
                "대출 관련 데이터(대여일/반납 예정일)는 본 API로 변경할 수 없습니다.\n\n" +
                "name, department는 필수값이며, email은 선택값입니다.",
        operationId = "updateLoan",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = UpdateLoanResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class, UpdateLoanService.ErrorCode::class])
    @PatchMapping("/loans/{loanId}")
    fun updateLoan(
        @PathVariable loanId: Long,
        @Valid @RequestBody request: UpdateLoanRequest,
    ): ResponseEntity<UpdateLoanResponse> {
        val serviceRequest =
            UpdateLoanService.Request(
                loanId = loanId,
                name = request.name,
                department = request.department,
                email = request.email,
            )
        val serviceResponse = updateLoanService.execute(serviceRequest)

        return ResponseEntity.ok(toResponse(serviceResponse.result))
    }

    private fun toResponse(loan: Loan): UpdateLoanResponse =
        UpdateLoanResponse(
            statusCode = HttpStatus.OK.value(),
            result =
                UpdateLoanResponse.Result(
                    loanId = requireNotNull(loan.id) { "저장된 대출의 ID가 존재하지 않습니다." },
                    bookItemId = requireNotNull(loan.bookItem.id) { "대출에 연결된 소장본의 ID가 존재하지 않습니다." },
                    callNumber = loan.bookItem.callNumber,
                    name = loan.name,
                    department = loan.department,
                    email = loan.email,
                    loanDate = loan.loanDate,
                    dueDate = loan.dueDate,
                    status = loan.computeLoanStatus(),
                ),
        )

    data class UpdateLoanRequest(
        @field:NotBlank(message = "대출자 이름은 필수입니다.")
        @Schema(description = "대출자 이름", example = "홍길동")
        val name: String,
        @field:NotBlank(message = "부서명은 필수입니다.")
        @Schema(description = "부서명", example = "개발팀")
        val department: String,
        @field:Email(message = "유효한 이메일 형식이 아닙니다.")
        @Schema(description = "대출자 이메일 (미입력 시 null)", example = "hong@example.com")
        val email: String? = null,
    )

    class UpdateLoanResponse(
        @Schema(description = "HTTP 상태 코드", example = "200")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<UpdateLoanResponse.Result>(statusCode = statusCode, result = result) {
        @Schema(name = "UpdateLoanResult")
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
            @Schema(description = "대출 상태", example = "BORROWED")
            val status: Loan.LoanStatus,
        )
    }
}
