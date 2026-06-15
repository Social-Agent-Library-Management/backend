package io.github.fnzl54.library.book.presentation.get

import io.github.fnzl54.library.book.service.ReadBookListService
import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.github.fnzl54.library.core.presentation.pagination.Pagination
import io.github.fnzl54.library.core.presentation.pagination.PaginationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Book", description = "책")
class ReadBookListController(
    private val readBookListService: ReadBookListService,
) {
    @Operation(
        summary = "도서 검색 API",
        description =
            "키워드(제목 / 저자 부분 검색)로 도서를 페이지 단위로 조회합니다.\n\n" +
                "키워드를 입력하지 않으면 전체 도서를 조회합니다.\n\n" +
                "응답의 items는 해당 도서의 소장본 목록(청구기호, 상태)입니다.",
        operationId = "readBookList",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = ReadBookListResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class])
    @GetMapping("/books")
    fun readBookList(
        @Valid @ParameterObject request: ReadBookListRequest,
    ): ResponseEntity<ReadBookListResponse> {
        val serviceRequest =
            ReadBookListService.Request(
                keyword = request.keyword?.trim(),
                pageable = request.toPageRequest(),
            )

        val serviceResponse = readBookListService.execute(serviceRequest)
        return ResponseEntity.ok(toResponse(serviceResponse.result))
    }

    private fun toResponse(pageResult: ReadBookListService.PageResult): ReadBookListResponse =
        ReadBookListResponse(
            statusCode = HttpStatus.OK.value(),
            result =
                ReadBookListResponse.Result(
                    pagination = pageResult.pagination,
                    books =
                        pageResult.books.map { book ->
                            ReadBookListResponse.BookDetail(
                                bookId = book.bookId,
                                isbn = book.isbn,
                                title = book.title,
                                author = book.author,
                                publisher = book.publisher,
                                items =
                                    book.items.map { item ->
                                        ReadBookListResponse.BookItemDetail(
                                            callNumber = item.callNumber,
                                            status = item.status,
                                        )
                                    },
                            )
                        },
                ),
        )

    data class ReadBookListRequest(
        @Schema(description = "검색 키워드 (제목/저자 부분 검색, 최대 100자)", example = "릴케")
        @field:Size(max = 100, message = "검색어는 최대 100자까지 입력 가능합니다.")
        val keyword: String? = null,
        @Schema(description = "페이지 번호 (1부터 시작, 기본값: 1)", example = "1")
        override val page: Int = 1,
        @Schema(description = "페이지 크기 (기본값: 10, 최대: 100)", example = "10")
        override val size: Int = 10,
    ) : PaginationRequest(page, size)

    class ReadBookListResponse(
        @Schema(description = "HTTP 상태 코드", example = "200")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<ReadBookListResponse.Result>(statusCode = statusCode, result = result) {
        data class Result(
            val pagination: Pagination,
            val books: List<BookDetail>,
        )

        data class BookDetail(
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
            @Schema(description = "소장본 목록")
            val items: List<BookItemDetail>,
        )

        data class BookItemDetail(
            @Schema(description = "청구기호", example = "A-001")
            val callNumber: String,
            @Schema(description = "대출 상태", example = "AVAILABLE")
            val status: BookItem.Status,
        )
    }
}
