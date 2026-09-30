package org.library.book.adapter

import org.library.book.application.port.BookSearchPort
import org.library.book.application.port.BookSearchResult
import org.library.book.application.port.toBookDocument
import org.library.book.domain.repository.BookRepository
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.countActiveItemsByBookId
import org.library.core.presentation.PageRequestParams
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
// opensearch 모드에서도 등록해 FallbackBookSearchAdapter의 대체 조회에 쓴다.
@ConditionalOnExpression("'\${search.engine:mysql}' == 'mysql' or '\${search.engine:mysql}' == 'opensearch'")
class MysqlLikeBookSearchAdapter(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) : BookSearchPort {

    override fun search(query: String?, page: PageRequestParams): BookSearchResult {
        val pageRequest = page.toPageRequest(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        val keyword = query?.trim()?.takeIf { it.isNotBlank() }
        val books = if (keyword == null) {
            bookRepository.findAllByDeletedAtIsNull(pageRequest)
        } else {
            bookRepository.searchActive(keyword, pageRequest)
        }

        val bookItemCounts = bookItemRepository.countActiveItemsByBookId(books.content.map { it.id })

        return BookSearchResult(
            page = books.map { it.toBookDocument(bookItemCounts[it.id] ?: 0L) },
            suggestions = emptyList(),
        )
    }
}
