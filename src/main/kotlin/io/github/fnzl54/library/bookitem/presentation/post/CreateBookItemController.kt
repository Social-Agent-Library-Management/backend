package io.github.fnzl54.library.bookitem.presentation.post

import io.github.fnzl54.library.bookitem.service.CreateBookItemService
import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
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

@RestController
@Tag(name = "BookItem", description = "소장본")
class CreateBookItemController(
    private val createBookItemService: CreateBookItemService,
) {
    @Operation(
        summary = "소장본 단건 등록 API",
        description =
            "소장본 정보를 등록합니다.\n\n" +
                "bookId에 해당하는 도서가 존재해야 하며, callNumber(관리번호)는 고유해야 합니다.\n\n" +
                "bookId, callNumber는 필수값입니다.",
        operationId = "createBookItem",
    )
    @ApiResponse(
        responseCode = "201",
        content = [Content(schema = Schema(implementation = CreateBookItemResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class, CreateBookItemService.ErrorCode::class])
    @PostMapping("/book-items")
    fun createBookItem(
        @Valid @RequestBody request: CreateBookItemRequest,
    ): ResponseEntity<CreateBookItemResponse> {
        val serviceRequest =
            CreateBookItemService.Request(
                bookId = request.bookId,
                callNumber = request.callNumber,
            )

        val serviceResponse = createBookItemService.execute(serviceRequest)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            toResponse(serviceResponse.result),
        )
    }

    private fun toResponse(bookItem: BookItem): CreateBookItemResponse =
        CreateBookItemResponse(
            statusCode = HttpStatus.CREATED.value(),
            result =
                CreateBookItemResponse.Result(
                    bookItemId = requireNotNull(bookItem.id) { "저장된 소장본의 ID가 존재하지 않습니다." },
                    bookId = requireNotNull(bookItem.book.id) { "소장본에 연결된 도서의 ID가 존재하지 않습니다." },
                    callNumber = bookItem.callNumber,
                    status = bookItem.status,
                ),
        )

    data class CreateBookItemRequest(
        @field:NotNull(message = "도서 ID는 필수입니다.")
        @Schema(description = "도서 ID", example = "1")
        val bookId: Long,
        @field:NotBlank(message = "관리번호는 필수입니다.")
        @Schema(description = "관리번호", example = "C001-001")
        val callNumber: String,
    )

    class CreateBookItemResponse(
        @Schema(description = "HTTP 상태 코드", example = "201")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<CreateBookItemResponse.Result>(statusCode = statusCode, result = result) {
        data class Result(
            @Schema(description = "소장본 ID", example = "1")
            val bookItemId: Long,
            @Schema(description = "도서 ID", example = "1")
            val bookId: Long,
            @Schema(description = "관리번호", example = "C001-001")
            val callNumber: String,
            @Schema(description = "상태", example = "AVAILABLE")
            val status: BookItem.Status,
        )
    }
}
