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

    @Query(
        value = """
        select t.id, t.created_at, t.updated_at, t.deleted_at, t.title, t.author, t.isbn, t.publisher
        from (
            select b.id, b.created_at, b.updated_at, b.deleted_at, b.title, b.author, b.isbn, b.publisher,
                   match(b.title, b.author) against(:q in natural language mode) as score
            from book b
            where b.deleted_at is null
              and match(b.title, b.author) against(:q in natural language mode)
        ) t
        where t.score > 0.0001
        order by t.score desc
        """,
        countQuery = """
        select count(*) from (
            select match(b.title, b.author) against(:q in natural language mode) as score
            from book b
            where b.deleted_at is null
              and match(b.title, b.author) against(:q in natural language mode)
        ) t
        where t.score > 0.0001
        """,
        nativeQuery = true,
    )
    fun searchFullText(@Param("q") q: String, pageable: Pageable): Page<Book>
}
