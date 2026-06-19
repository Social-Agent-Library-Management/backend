package io.github.fnzl54.library.book.presentation.admin

import io.github.fnzl54.library.book.search.BookReindexService
import io.github.fnzl54.library.config.swagger.ApiErrorCode
import io.github.fnzl54.library.core.exception.error.GlobalErrorCode
import io.github.fnzl54.library.core.presentation.SuccessResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Book Admin", description = "도서 검색 관리")
class ReindexBookController(
    private val bookReindexService: BookReindexService,
) {
    @Operation(
        summary = "도서 ES 재색인 API",
        description = "삭제되지 않은 전체 도서를 Elasticsearch로 벌크 재색인합니다. 초기 적재 및 정합성 보정에 사용합니다.",
        operationId = "reindexBooks",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = ReindexBookResponse::class))],
    )
    @ApiErrorCode(errorCodes = [GlobalErrorCode::class])
    @PostMapping("/admin/search/reindex")
    fun reindex(): ResponseEntity<ReindexBookResponse> {
        val indexedCount = bookReindexService.reindexAll()
        return ResponseEntity.ok(
            ReindexBookResponse(
                statusCode = HttpStatus.OK.value(),
                result = ReindexBookResponse.Result(indexedCount = indexedCount),
            ),
        )
    }

    class ReindexBookResponse(
        @Schema(description = "HTTP 상태 코드", example = "200")
        override val statusCode: Int,
        result: Result,
    ) : SuccessResponse<ReindexBookResponse.Result>(statusCode = statusCode, result = result) {
        data class Result(
            @Schema(description = "색인된 도서 수", example = "1024")
            val indexedCount: Long,
        )
    }
}
