package org.library.adapter.book.search.mysql

import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.book.application.port.toBookDocument
import org.library.book.domain.BookRepository
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.countActiveItemsByBookId
import org.library.core.presentation.PageRequestParams
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
@ConditionalOnProperty(name = ["search.engine"], havingValue = "mysql_fulltext")
class MysqlFullTextBookSearchAdapter(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) : BookSearchPort {

    override fun search(query: String?, page: PageRequestParams): Page<BookDocument> {
        val keyword = query?.trim()?.takeIf { it.isNotBlank() }
        val pageRequest = page.toPageRequest(Sort.by(Sort.Direction.DESC, "createdAt"))
        val books = when {
            keyword == null -> bookRepository.findAllByDeletedAtIsNull(pageRequest)
            keyword.length < 2 -> bookRepository.searchActive(keyword, pageRequest)
            else -> bookRepository.searchFullText(keyword, page.toPageRequest())
        }

        val bookItemCounts = bookItemRepository.countActiveItemsByBookId(books.content.map { it.id })

        return books.map { it.toBookDocument(bookItemCounts[it.id] ?: 0L) }
    }
}
