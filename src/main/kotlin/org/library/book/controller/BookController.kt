package org.library.book.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.library.book.application.CreateBookService
import org.library.book.domain.error.BookError
import org.library.book.dto.BookResponse
import org.library.core.application.getOrThrow
import org.library.core.swagger.ApiErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Book", description = "도서 관리 API")
@RestController
@RequestMapping("/books")
class BookController(
    private val createBookService: CreateBookService,
) {

    @Operation(
        summary = "도서 등록",
        description = "새 도서를 등록한다. ISBN은 미입력할 수 있고, 값이 있으면 중복될 수 없다.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "도서 등록 성공",
        content = [Content(schema = Schema(implementation = BookResponse::class))],
    )
    @ApiErrorCode(errorCodes = [BookError::class], only = ["DUPLICATE_ISBN"])
    @PostMapping
    fun create(@Valid @RequestBody request: CreateBookService.Request): ResponseEntity<BookResponse> {
        val book = createBookService.execute(request).getOrThrow()
        return ResponseEntity.status(HttpStatus.CREATED).body(book)
    }
}
