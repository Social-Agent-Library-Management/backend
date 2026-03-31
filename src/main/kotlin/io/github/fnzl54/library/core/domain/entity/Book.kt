package io.github.fnzl54.library.core.domain.entity

import io.github.fnzl54.library.core.domain.share.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "book")
class Book(
    @Column(unique = true)
    val isbn: String? = null,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false)
    val author: String,
    val publisher: String? = null,
) : BaseEntity()
