package org.library.devtools.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.library.devtools.db.MysqlConfig
import org.library.devtools.db.OpenSearchConfig
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}
private val objectMapper = JsonMapper.builder().build()

private data class BookIndexRow(
    val id: Long,
    val title: String,
    val author: String,
    val publisher: String,
    val isbn: String?,
    val createdAt: LocalDateTime,
)

fun main() {
    OpenSearchConfig.newClient().use { client ->
        recreateIndex(client)

        val books = readBooksFromMysql()
        log.info { "MySQL에서 book ${books.size}건 조회 완료" }

        bulkIndex(client, books)
        client.performRequest(Request("POST", "/books/_refresh"))
        log.info { "OpenSearch 'books' 인덱스에 ${books.size}건 색인 완료" }
    }
}

private fun recreateIndex(client: RestClient) {
    runCatching { client.performRequest(Request("DELETE", "/books")) }
        .onFailure { log.info { "기존 인덱스 없음(정상): ${it.message}" } }

    val create = Request("PUT", "/books")
    create.setJsonEntity(File("opensearch/books_index.json").readText())
    client.performRequest(create)
    log.info { "'books' 인덱스를 nori 매핑으로 재생성했습니다." }
}

private fun readBooksFromMysql(): List<BookIndexRow> =
    MysqlConfig.jdbcTemplate.query(
        "SELECT id, title, author, publisher, isbn, created_at FROM book WHERE deleted_at IS NULL",
    ) { rs, _ ->
        BookIndexRow(
            id = rs.getLong("id"),
            title = rs.getString("title"),
            author = rs.getString("author"),
            publisher = rs.getString("publisher"),
            isbn = rs.getString("isbn"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
        )
    }

private fun bulkIndex(client: RestClient, books: List<BookIndexRow>) {
    books.chunked(1000).forEachIndexed { chunkIndex, chunk ->
        val body = buildString {
            chunk.forEach { book ->
                append(objectMapper.writeValueAsString(mapOf("index" to mapOf("_index" to "books", "_id" to book.id))))
                append('\n')
                append(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "bookId" to book.id,
                            "title" to book.title,
                            "author" to book.author,
                            "publisher" to book.publisher,
                            "isbn" to book.isbn,
                            "createdAt" to book.createdAt.toString(),
                        ),
                    ),
                )
                append('\n')
            }
        }

        val request = Request("POST", "/_bulk")
        request.setEntity(StringEntity(body, ContentType.create("application/x-ndjson", Charsets.UTF_8)))
        val response = client.performRequest(request)
        val root = objectMapper.readTree(EntityUtils.toString(response.entity))
        if (root.path("errors").asBoolean(false)) {
            log.warn { "bulk 청크 ${chunkIndex + 1}에서 일부 색인 실패 발생: ${root.toString().take(500)}" }
        } else {
            log.info { "bulk 청크 ${chunkIndex + 1} (${chunk.size}건) 색인 완료" }
        }
    }
}
