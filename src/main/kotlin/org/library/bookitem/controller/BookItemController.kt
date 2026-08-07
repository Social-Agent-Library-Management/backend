package org.library.bookitem.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.library.bookitem.application.CreateBookItemService
import org.library.bookitem.domain.error.BookItemError
import org.library.core.application.getOrThrow
import org.library.core.swagger.ApiErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Tag(name = "BookItem", description = "소장본 관리 API")
@RestController
class BookItemController(
    private val createBookItemService: CreateBookItemService,
) {

    @Operation(
        summary = "소장본 등록",
        description = "도서에 소장본을 등록한다. 관리번호는 (주제)-(번호) 형식이며 전체 고유해야 한다. 등록 시 상태 기본값은 AVAILABLE.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "소장본 등록 성공",
        content = [Content(schema = Schema(implementation = CreateBookItemService.Response::class))],
    )
    @ApiErrorCode(errorCodes = [BookItemError::class], only = ["BOOK_NOT_FOUND", "INVALID_MANAGEMENT_NUMBER_FORMAT", "DUPLICATE_MANAGEMENT_NUMBER"])
    @PostMapping("/books/{bookId}/bookitems")
    fun create(
        @Parameter(description = "도서 ID") @PathVariable bookId: Long,
        @Valid @RequestBody request: CreateBookItemService.Request,
    ): ResponseEntity<CreateBookItemService.Response> {
        val bookItem = createBookItemService.execute(bookId, request).getOrThrow()
        return ResponseEntity.status(HttpStatus.CREATED).body(bookItem)
    }
}
