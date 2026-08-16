package org.library.book.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BookRepository : JpaRepository<Book, Long> {

    fun findByIsbnAndDeletedAtIsNull(isbn: String): Book?

    fun findByIdAndDeletedAtIsNull(id: Long): Book?

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Book>

    @Query(
        """
        select b from Book b
        where b.deletedAt is null
          and (lower(b.title) like lower(concat('%', :q, '%'))
               or lower(b.author) like lower(concat('%', :q, '%')))
        """,
    )
    fun searchActive(@Param("q") q: String, pageable: Pageable): Page<Book>

    @Query(
        """
        select b from Book b
        where b.deletedAt is null
          and (lower(b.title) like lower(concat('%', :q, '%'))
               or lower(b.author) like lower(concat('%', :q, '%')))
        """,
    )
    fun findAllByTitleContainingOrAuthorContaining(@Param("q") q: String): List<Book>
}
