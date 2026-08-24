package org.library.book.domain.repository

import org.library.book.domain.entity.BookOpenSearchOutbox
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface BookOpenSearchOutboxRepository : JpaRepository<BookOpenSearchOutbox, Long> {

    fun findAllByBookId(bookId: Long): List<BookOpenSearchOutbox>

    fun findAllByNextAttemptAtLessThanEqual(nextAttemptAt: LocalDateTime, pageable: Pageable): List<BookOpenSearchOutbox>
}