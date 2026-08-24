package org.library.external.opensearch.dto

import org.library.book.domain.entity.Book
import java.time.LocalDateTime
import java.time.ZoneId

data class BookIndexDocument(
    val bookId: Long,
    val title: String,
    val author: String,
    val publisher: String,
    val isbn: String?,
    val createdAt: LocalDateTime,
    val version: Long,
)

fun Book.toBookIndexDocument(): BookIndexDocument = BookIndexDocument(
    bookId = id,
    title = title,
    author = author,
    publisher = publisher,
    isbn = isbn,
    createdAt = createdAt,
    version = updatedAt.toEpochMillis(),
)

fun BookIndexDocument.toSourceMap(): Map<String, Any?> = mapOf(
    "bookId" to bookId,
    "title" to title,
    "author" to author,
    "publisher" to publisher,
    "isbn" to isbn,
    "createdAt" to createdAt.toString(),
)

fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
