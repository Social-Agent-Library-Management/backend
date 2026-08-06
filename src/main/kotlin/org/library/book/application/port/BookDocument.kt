package org.library.book.application.port

data class BookDocument(
    val id: Long,
    val title: String,
    val author: String,
    val isbn: String?,
)
