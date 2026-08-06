package org.library.book.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.library.book.application.CreateBookService
import org.library.book.application.DeleteBookService
import org.library.book.application.GetBookService
import org.library.book.application.UpdateBookService
import org.library.book.domain.error.BookError
import org.library.book.dto.BookResponse
import org.library.core.application.getOrThrow
import org.library.core.swagger.ApiErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Book", description = "도서 관리 API")
@RestController
@RequestMapping("/books")
class BookController(
    private val createBookService: CreateBookService,
    private val getBookService: GetBookService,
    private val updateBookService: UpdateBookService,
    private val deleteBookService: DeleteBookService,
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

    @Operation(summary = "도서 단건 조회", description = "ID로 도서 한 건을 조회한다.")
    @ApiErrorCode(errorCodes = [BookError::class], only = ["NOT_FOUND"])
    @GetMapping("/{id}")
    fun get(@Parameter(description = "도서 ID") @PathVariable id: Long): BookResponse =
        getBookService.execute(id).getOrThrow()

    @Operation(
        summary = "도서 수정",
        description = "도서의 제목·저자·ISBN을 수정한다. 요청 본문이 곧 최종 상태이며, ISBN을 생략하면 기존 값이 지워진다.",
    )
    @ApiErrorCode(errorCodes = [BookError::class], only = ["NOT_FOUND", "DUPLICATE_ISBN"])
    @PatchMapping("/{id}")
    fun update(
        @Parameter(description = "도서 ID") @PathVariable id: Long,
        @Valid @RequestBody request: UpdateBookService.Request,
    ): BookResponse =
        updateBookService.execute(id, request).getOrThrow()

    @Operation(summary = "도서 삭제", description = "도서를 소프트 삭제한다.")
    @ApiResponse(responseCode = "204", description = "도서 삭제 성공")
    @ApiErrorCode(errorCodes = [BookError::class], only = ["NOT_FOUND"])
    @DeleteMapping("/{id}")
    fun delete(@Parameter(description = "도서 ID") @PathVariable id: Long): ResponseEntity<Unit> {
        deleteBookService.execute(id).getOrThrow()
        return ResponseEntity.noContent().build()
    }
}
