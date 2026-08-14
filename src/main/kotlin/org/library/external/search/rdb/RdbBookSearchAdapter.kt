package org.library.external.search.rdb

import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.book.domain.Book
import org.library.book.domain.BookRepository
import org.library.bookitem.domain.BookItemRepository
import org.library.core.presentation.PageRequestParams
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
@ConditionalOnProperty(name = ["search.engine"], havingValue = "rdb", matchIfMissing = true)
class RdbBookSearchAdapter(
    private val bookRepository: BookRepository,
    private val bookItemRepository: BookItemRepository,
) : BookSearchPort {

    override fun search(query: String?, page: PageRequestParams): Page<BookDocument> {
        val pageRequest = page.toPageRequest(Sort.by(Sort.Direction.DESC, "createdAt"))
        val keyword = query?.trim()?.takeIf { it.isNotBlank() }
        val books = if (keyword == null) {
            bookRepository.findAllByDeletedAtIsNull(pageRequest)
        } else {
            bookRepository.searchActive(keyword, pageRequest)
        }

        val bookIds = books.content.map { it.id }
        val bookItemCounts = if (bookIds.isEmpty()) {
            emptyMap()
        } else {
            bookItemRepository.countActiveByBookIdIn(bookIds).associate { it.bookId to it.itemCount }
        }

        return books.map { it.toDocument(bookItemCounts[it.id] ?: 0L) }
    }

    private fun Book.toDocument(bookItemCount: Long) = BookDocument(
        id = id,
        title = title,
        author = author,
        publisher = publisher,
        isbn = isbn,
        bookItemCount = bookItemCount,
    )
}
