package org.library.external.opensearch.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import org.library.book.domain.repository.BookOpenSearchOutboxRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class BookOpenSearchOutboxScheduler(
    private val bookOpenSearchOutboxRepository: BookOpenSearchOutboxRepository,
    private val bookIndexSynchronizer: BookIndexSynchronizer,
    @Value("\${search.index.outbox.batch-size:100}") private val batchSize: Int,
) {

    @Scheduled(fixedDelayString = "\${search.index.outbox.poll-interval:PT30S}")
    fun retryPending() {
        val due = bookOpenSearchOutboxRepository.findAllByNextAttemptAtLessThanEqual(
            LocalDateTime.now(),
            PageRequest.of(0, batchSize, Sort.by("nextAttemptAt")),
        )
        if (due.isEmpty()) return

        due.map { it.bookId }.distinct().forEach { bookId ->
            runCatching { bookIndexSynchronizer.sync(bookId) }
                .onFailure { log.warn(it) { "outbox 재시도 중 예외가 발생했습니다. bookId=$bookId" } }
        }
    }
}
