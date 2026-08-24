package org.library.book.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.library.core.domain.BaseEntity
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(name = "book_opensearch_outbox")
class BookOpenSearchOutbox(
    bookId: Long,
    nextAttemptAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity() {

    @Column(nullable = false)
    var bookId: Long = bookId
        protected set

    @Column(nullable = false)
    var retryCount: Int = 0
        protected set

    @Column(nullable = false)
    var nextAttemptAt: LocalDateTime = nextAttemptAt
        protected set

    @Column(length = LAST_ERROR_MAX_LENGTH)
    var lastError: String? = null
        protected set

    fun markRetried(backoff: Duration, error: String?) {
        retryCount += 1
        nextAttemptAt = LocalDateTime.now().plus(backoff)
        lastError = error?.take(LAST_ERROR_MAX_LENGTH)
    }

    companion object {
        const val LAST_ERROR_MAX_LENGTH = 500
    }
}