package io.github.fnzl54.library.core.domain.repository

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.fnzl54.library.core.domain.entity.BookItem
import io.github.fnzl54.library.core.domain.entity.QBookItem.bookItem
import org.springframework.stereotype.Repository

@Repository
class BookQueryRepository(
    private val jpaQueryFactory: JPAQueryFactory,
) {
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
}
