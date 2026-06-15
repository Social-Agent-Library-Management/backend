package io.github.fnzl54.library.core.domain.repository

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.entity.QBook.book
import io.github.fnzl54.library.core.domain.entity.QBookItem.bookItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository

@Repository
class BookQueryRepository(
    private val jpaQueryFactory: JPAQueryFactory,
) {
    data class BookSearchRequest(
        val keyword: String?,
    )

    data class BookSummary(
        val bookId: Long,
        val isbn: String?,
        val title: String,
        val author: String,
        val publisher: String?,
    )

    data class BookItemSummary(
        val bookId: Long,
        val callNumber: String,
        val status: BookItem.Status,
    )

    fun findBooksByKeyword(
        request: BookSearchRequest,
        pageable: Pageable,
    ): Page<BookSummary> {
        val predicates =
            arrayOf(
                book.deleted.isFalse,
                keywordContains(request.keyword),
            )

        val content =
            jpaQueryFactory
                .select(
                    Projections.constructor(
                        BookSummary::class.java,
                        book.id,
                        book.isbn,
                        book.title,
                        book.author,
                        book.publisher,
                    ),
                ).from(book)
                .where(*predicates)
                .orderBy(book.title.asc(), book.id.asc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        val countQuery =
            jpaQueryFactory
                .select(book.count())
                .from(book)
                .where(*predicates)

        return PageableExecutionUtils.getPage(content, pageable) { countQuery.fetchOne() ?: 0L }
    }

    fun findBookItemsByBookIds(bookIds: Collection<Long>): List<BookItemSummary> {
        if (bookIds.isEmpty()) return emptyList()

        return jpaQueryFactory
            .select(
                Projections.constructor(
                    BookItemSummary::class.java,
                    bookItem.book.id,
                    bookItem.callNumber,
                    bookItem.status,
                ),
            ).from(bookItem)
            .where(
                bookItem.deleted.isFalse,
                bookItem.book.id.`in`(bookIds),
            )
            .orderBy(bookItem.callNumber.asc())
            .fetch()
    }

    private fun keywordContains(keyword: String?): BooleanExpression? =
        keyword?.takeIf { it.isNotBlank() }?.let { kw ->
            book.title.containsIgnoreCase(kw)
                .or(book.author.containsIgnoreCase(kw))
        }
}
