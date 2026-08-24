package org.library.external.opensearch.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import org.library.book.application.event.BookChangedEvent
import org.library.book.domain.entity.BookOpenSearchOutbox
import org.library.book.domain.repository.BookOpenSearchOutboxRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val log = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class BookIndexSyncListener(
    private val bookOpenSearchOutboxRepository: BookOpenSearchOutboxRepository,
    private val bookIndexSynchronizer: BookIndexSynchronizer,
) {


    @EventListener
    fun record(event: BookChangedEvent) {
        bookOpenSearchOutboxRepository.save(BookOpenSearchOutbox(bookId = event.bookId))
    }

    @Async("bookIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sync(event: BookChangedEvent) {
        runCatching { bookIndexSynchronizer.sync(event.bookId) }
            .onFailure { log.warn(it) { "커밋 직후 색인 동기화 실패, outbox 재시도로 이관합니다. bookId=${event.bookId}" } }
    }
}
