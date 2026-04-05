package io.github.fnzl54.library.core.domain.repository

import io.github.fnzl54.library.core.domain.entity.BookItem
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface BookItemRepository : JpaRepository<BookItem, Long> {
    fun existsByCallNumber(callNumber: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByCallNumber(callNumber: String): BookItem?
}
