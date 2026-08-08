package org.library.loan.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.library.core.application.getOrThrow
import org.library.core.swagger.ApiErrorCode
import org.library.loan.application.CreateLoanService
import org.library.loan.domain.error.LoanError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Loan", description = "대출 관리 API")
@RestController
@RequestMapping("/loans")
class LoanController(
    private val createLoanService: CreateLoanService,
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
}
