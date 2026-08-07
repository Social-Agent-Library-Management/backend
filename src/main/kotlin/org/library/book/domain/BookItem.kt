package org.library.book.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.library.core.domain.BaseEntity

@Entity
@Table(name = "book_items")
class BookItem(
    book: Book,
    managementNumber: String,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    var book: Book = book
        protected set

    @Column(nullable = false)
    var managementNumber: String = managementNumber
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BookItemStatus = BookItemStatus.AVAILABLE
        protected set

    init {
        require(MANAGEMENT_NUMBER_REGEX.matches(managementNumber)) {
            "관리번호는 (주제)-(번호) 형식이어야 합니다."
        }
    }

    companion object {

        val MANAGEMENT_NUMBER_REGEX = Regex("^[^-\\s]+-\\d+$")
    }
}
