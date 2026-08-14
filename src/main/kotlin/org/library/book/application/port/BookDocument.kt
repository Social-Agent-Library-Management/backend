package org.library.book.application.port

data class BookDocument(
    val id: Long,
    val title: String,
    val author: String,
    val publisher: String,
    val isbn: String?,
    val bookItemCount: Long,
)
