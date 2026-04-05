package io.github.fnzl54.library.loan.presentation.post

import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.entity.Loan
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.github.fnzl54.library.loan.service.CreateLoanService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@Tag(name = "Loan", description = "대출")
class CreateLoanController(
    private val createLoanService: CreateLoanService,
) {
    @Operation(
        summary = "대출 등록 API",
        description =
            "소장본 관리번호를 기준으로 대출을 등록합니다.\n\n" +
                "callNumber에 해당하는 소장본이 존재해야 하며, 대출 가능(AVAILABLE) 상태여야 합니다.\n\n" +
                "callNumber, name, department, loanDate, dueDate는 필수값입니다.",
        operationId = "createLoan",
    )
    @ApiResponse(
        responseCode = "201",
        content = [Content(schema = Schema(implementation = CreateLoanResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class, CreateLoanService.ErrorCode::class])
    @PostMapping("/loans")
    fun createLoan(
        @Valid @RequestBody request: CreateLoanRequest,
    ): ResponseEntity<CreateLoanResponse> {
        val serviceRequest =
            CreateLoanService.Request(
                callNumber = request.callNumber,
                name = request.name,
                department = request.department,
                email = request.email,
                loanDate = request.loanDate,
                dueDate = request.dueDate,
            )

        val serviceResponse = createLoanService.execute(serviceRequest)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            toResponse(serviceResponse.result),
        )
    }

    private fun toResponse(loan: Loan): CreateLoanResponse =
        CreateLoanResponse(
            statusCode = HttpStatus.CREATED.value(),
            result =
                CreateLoanResponse.Result(
                    loanId = requireNotNull(loan.id) { "저장된 대출의 ID가 존재하지 않습니다." },
                    bookItemId = requireNotNull(loan.bookItem.id) { "대출에 연결된 소장본의 ID가 존재하지 않습니다." },
                    callNumber = loan.bookItem.callNumber,
                    name = loan.name,
                    department = loan.department,
                    email = loan.email,
                    loanDate = loan.loanDate,
                    dueDate = loan.dueDate,
                    status = loan.bookItem.status,
                ),
        )

    data class CreateLoanRequest(
        @field:NotBlank(message = "관리번호는 필수입니다.")
        @Schema(description = "소장본 관리번호", example = "C001-001")
        val callNumber: String,
        @field:NotBlank(message = "대출자 이름은 필수입니다.")
        @Schema(description = "대출자 이름", example = "홍길동")
        val name: String,
        @field:NotBlank(message = "부서명은 필수입니다.")
        @Schema(description = "부서명", example = "개발팀")
        val department: String,
        @Schema(description = "대출자 이메일 (미입력 시 null)", example = "hong@example.com")
        val email: String? = null,
        @field:NotNull(message = "대여일은 필수입니다.")
        @Schema(description = "대여일", example = "2026-04-04")
        val loanDate: LocalDate,
        @field:NotNull(message = "반납 예정일은 필수입니다.")
        @Schema(description = "반납 예정일", example = "2026-04-18")
        val dueDate: LocalDate,
    )

    class CreateLoanResponse(
        @Schema(description = "HTTP 상태 코드", example = "201")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<CreateLoanResponse.Result>(statusCode = statusCode, result = result) {
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
            @Schema(description = "소장본 상태", example = "BORROWED")
            val status: BookItem.Status,
        )
    }
}
