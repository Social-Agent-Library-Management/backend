package org.library.external.opensearch.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.LocalDateTime
import org.library.book.domain.repository.BookOpenSearchOutboxRepository
import org.library.book.domain.repository.BookRepository
import org.library.external.opensearch.dto.toBookIndexDocument
import org.library.external.opensearch.dto.toEpochMillis
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class BookIndexSynchronizer(
    private val bookRepository: BookRepository,
    private val bookOpenSearchOutboxRepository: BookOpenSearchOutboxRepository,
    private val openSearchBookIndexer: OpenSearchBookIndexer,
    @Value("\${search.index.outbox.backoff-base-seconds:10}") private val backoffBaseSeconds: Long,
    @Value("\${search.index.outbox.max-backoff-seconds:600}") private val maxBackoffSeconds: Long,
) {


    @Transactional
    fun sync(bookId: Long) {
        val outboxRows = bookOpenSearchOutboxRepository.findAllByBookIdForUpdate(bookId)
        if (outboxRows.isEmpty()) return
        val outboxIds = outboxRows.map { it.id }

        runCatching { indexOrDelete(bookId) }
            .onSuccess { bookOpenSearchOutboxRepository.deleteAllById(outboxIds) }
            .onFailure { e ->
                log.warn(e) { "OpenSearch 색인 동기화 실패, 재시도를 예약합니다. bookId=$bookId" }
                markRetried(outboxIds, e)
            }
    }

    private fun indexOrDelete(bookId: Long) {
        val book = bookRepository.findById(bookId).orElse(null)
        if (book == null || book.isDeleted) {
            val version = book?.updatedAt?.toEpochMillis() ?: LocalDateTime.now().toEpochMillis()
            openSearchBookIndexer.delete(bookId, version)
        } else {
            openSearchBookIndexer.index(book.toBookIndexDocument())
        }
    }

    private fun markRetried(outboxIds: List<Long>, error: Throwable) {
        val rows = bookOpenSearchOutboxRepository.findAllById(outboxIds)
        rows.forEach {
            if (it.retryCount >= MAX_RETRY_COUNT) {
                log.error(error) { "outbox 재시도 한도 초과, 수동 확인 필요. id=${it.id}, bookId=${it.bookId}" }
            }
            it.markRetried(nextBackoff(it.retryCount), error.message)
        }
        bookOpenSearchOutboxRepository.saveAll(rows)
    }

    private fun nextBackoff(retryCount: Int): Duration {
        val exponent = minOf(retryCount, MAX_BACKOFF_EXPONENT)
        val seconds = minOf(backoffBaseSeconds * (1L shl exponent), maxBackoffSeconds)
        return Duration.ofSeconds(seconds)
    }

    companion object {
        private const val MAX_BACKOFF_EXPONENT = 30
        private const val MAX_RETRY_COUNT = 10
    }
}
