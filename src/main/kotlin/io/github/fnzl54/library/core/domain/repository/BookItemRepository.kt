package io.github.fnzl54.library.core.domain.repository

import io.github.fnzl54.library.core.domain.entity.BookItem
import org.springframework.data.jpa.repository.JpaRepository

interface BookItemRepository : JpaRepository<BookItem, Long> {
    fun existsByCallNumber(callNumber: String): Boolean
}
