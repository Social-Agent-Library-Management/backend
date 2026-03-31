package io.github.fnzl54.library.book.presentation.post

import io.github.fnzl54.library.book.service.CreateBookService
import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.Book
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Book", description = "책")
class CreateBookController(
    private val createBookService: CreateBookService,
) {
    @Operation(
        summary = "도서 단건 등록 API",
        description =
            "도서 정보를 등록합니다.\n\n" +
                "ISBN이 있는 경우 ISBN 중복 여부를 확인하고, ISBN이 없는 경우 제목 중복 여부를 확인합니다.\n\n" +
                "title, author는 필수값입니다.",
        operationId = "createBook",
    )
    @ApiResponse(
        responseCode = "201",
        content = [Content(schema = Schema(implementation = CreateBookResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class, CreateBookService.ErrorCode::class])
    @PostMapping("/books")
    fun createBook(
        @Valid @RequestBody request: CreateBookRequest,
    ): ResponseEntity<CreateBookResponse> {
        val serviceRequest =
            CreateBookService.Request(
                title = request.title,
                author = request.author,
                isbn = request.isbn,
                publisher = request.publisher,
            )

        val result = createBookService.execute(serviceRequest)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            toResponse(result.result),
        )
    }

    private fun toResponse(book: Book): CreateBookResponse =
        CreateBookResponse(
            statusCode = HttpStatus.CREATED.value(),
            result =
                CreateBookResponse.Result(
                    bookId = requireNotNull(book.id) { "저장된 도서의 ID가 존재하지 않습니다." },
                    isbn = book.isbn,
                    title = book.title,
                    author = book.author,
                    publisher = book.publisher,
                ),
        )

    data class CreateBookRequest(
        @field:NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "도서 제목", example = "말테의 수기")
        val title: String,
        @field:NotBlank(message = "저자는 필수입니다.")
        @Schema(description = "저자", example = "라이너 마리아 릴케")
        val author: String,
        @Schema(description = "ISBN", example = "9788937425370", nullable = true)
        val isbn: String? = null,
        @Schema(description = "출판사", example = "민음사", nullable = true)
        val publisher: String? = null,
    )

    class CreateBookResponse(
        @Schema(description = "HTTP 상태 코드", example = "201")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<CreateBookResponse.Result>(statusCode = statusCode, result = result) {
        data class Result(
            @Schema(description = "도서 ID", example = "1")
            val bookId: Long,
            @Schema(description = "ISBN", example = "9788937425370", nullable = true)
            val isbn: String?,
            @Schema(description = "도서 제목", example = "말테의 수기")
            val title: String,
            @Schema(description = "저자", example = "라이너 마리아 릴케")
            val author: String,
            @Schema(description = "출판사", example = "민음사", nullable = true)
            val publisher: String?,
        )
    }
}
