package org.library.book.domain.repository

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.library.book.domain.entity.BookOpenSearchOutbox
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface BookOpenSearchOutboxRepository : JpaRepository<BookOpenSearchOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from BookOpenSearchOutbox o where o.bookId = :bookId")
    fun findAllByBookIdForUpdate(@Param("bookId") bookId: Long): List<BookOpenSearchOutbox>

    fun findAllByNextAttemptAtLessThanEqual(nextAttemptAt: LocalDateTime, pageable: Pageable): List<BookOpenSearchOutbox>
}