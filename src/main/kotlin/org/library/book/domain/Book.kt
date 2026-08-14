package org.library.book.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.library.core.domain.BaseEntity

@Entity
@Table(name = "book")
class Book(
    title: String,
    author: String,
    isbn: String?,
    publisher: String,
) : BaseEntity() {

    @Column(nullable = false)
    var title: String = title
        protected set

    @Column(nullable = false)
    var author: String = author
        protected set

    @Column
    var isbn: String? = normalizeIsbn(isbn)
        protected set

    @Column(nullable = false)
    var publisher: String = publisher
        protected set

    init {
        require(title.isNotBlank()) { "제목은 비어 있을 수 없습니다." }
        require(author.isNotBlank()) { "저자는 비어 있을 수 없습니다." }
        require(publisher.isNotBlank()) { "출판사는 비어 있을 수 없습니다." }
    }

    fun update(title: String, author: String, isbn: String?, publisher: String) {
        require(title.isNotBlank()) { "제목은 비어 있을 수 없습니다." }
        require(author.isNotBlank()) { "저자는 비어 있을 수 없습니다." }
        require(publisher.isNotBlank()) { "출판사는 비어 있을 수 없습니다." }
        this.title = title
        this.author = author
        this.isbn = normalizeIsbn(isbn)
        this.publisher = publisher
    }

    companion object {

        fun normalizeIsbn(isbn: String?): String? = isbn?.trim()?.takeIf { it.isNotBlank() }
    }
}
