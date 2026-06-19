package io.github.fnzl54.library.book.search.event

import io.github.fnzl54.library.book.search.BookIndexService
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val log = KotlinLogging.logger {}

@Component
class BookIndexEventListener(
    private val bookIndexService: BookIndexService,
) {
    /**
     * DB 커밋 이후(AFTER_COMMIT) 비동기로 ES에 반영한다.
     * - 커밋 후 실행이라 ES 실패가 DB 트랜잭션을 되돌리지 않는다(데이터 유실 없음).
     * - 실패는 로깅만 하고 벌크 재색인(BookReindexService)으로 보정한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: BookChangedEvent) {
        runCatching { bookIndexService.reconcile(event.bookId) }
            .onFailure {
                log.error(it) { "ES 색인 실패: bookId=${event.bookId} (벌크 재색인으로 보정 필요)" }
            }
    }
}
