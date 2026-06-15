package io.github.fnzl54.library.book.service

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.fnzl54.library.core.application.BaseRequest
import io.github.fnzl54.library.core.application.BaseResponse
import io.github.fnzl54.library.core.application.BaseService
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.repository.BookQueryRepository
import io.github.fnzl54.library.core.presentation.pagination.Pagination
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReadBookListService(
    private val bookQueryRepository: BookQueryRepository,
) : BaseService<ReadBookListService.Request, ReadBookListService.Response>() {
    override fun doExecute(request: Request): Response {
        val searchRequest = BookQueryRepository.BookSearchRequest(keyword = request.keyword)
        val bookPage = bookQueryRepository.findBooksByKeyword(searchRequest, request.pageable)

        val bookIds = bookPage.content.map { it.bookId }
        val itemsByBookId =
            bookQueryRepository
                .findBookItemsByBookIds(bookIds)
                .groupBy { it.bookId }

        val books =
            bookPage.content.map { summary ->
                BookDetail(
                    bookId = summary.bookId,
                    isbn = summary.isbn,
                    title = summary.title,
                    author = summary.author,
                    publisher = summary.publisher,
                    items =
                        itemsByBookId[summary.bookId].orEmpty().map { item ->
                            BookItemDetail(
                                callNumber = item.callNumber,
                                status = item.status,
                            )
                        },
                )
            }

        return Response(
            PageResult(
                pagination = Pagination.from(bookPage),
                books = books,
            ),
        )
    }

    data class Request(
        val keyword: String?,
        val pageable: Pageable,
    ) : BaseRequest {
        @get:JsonIgnore
        override val isValid: Boolean
            get() = true
    }

    data class PageResult(
        val pagination: Pagination,
        val books: List<BookDetail>,
    )

    data class BookDetail(
        val bookId: Long,
        val isbn: String?,
        val title: String,
        val author: String,
        val publisher: String?,
        val items: List<BookItemDetail>,
    )

    data class BookItemDetail(
        val callNumber: String,
        val status: BookItem.Status,
    )

    class Response(pageResult: PageResult) : BaseResponse<PageResult>(result = pageResult)
}
