package io.github.fnzl54.library.core.domain.repository

import io.github.fnzl54.library.core.domain.entity.Book
import org.springframework.data.jpa.repository.JpaRepository

interface BookRepository : JpaRepository<Book, Long> {
    fun existsByIsbnAndDeletedFalse(isbn: String): Boolean

    fun existsByTitleAndDeletedFalse(title: String): Boolean
}
