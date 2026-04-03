package io.github.fnzl54.library.core.domain.entity

import io.github.fnzl54.library.core.domain.share.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "book_item")
class BookItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    val book: Book,
    @Column(nullable = false, unique = true)
    val callNumber: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: Status = Status.AVAILABLE,
) : BaseEntity() {
    enum class Status {
        AVAILABLE,
        BORROWED,
    }
}
