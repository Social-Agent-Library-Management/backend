package org.library.book.application.port

import org.library.book.domain.Book

data class BookDocument(
    val id: Long,
    val title: String,
    val author: String,
    val publisher: String,
    val isbn: String?,
    val bookItemCount: Long,
)

fun Book.toBookDocument(bookItemCount: Long) = BookDocument(
    id = id,
    title = title,
    author = author,
    publisher = publisher,
    isbn = isbn,
    bookItemCount = bookItemCount,
)
