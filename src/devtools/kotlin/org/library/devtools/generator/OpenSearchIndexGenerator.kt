package org.library.devtools.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.library.external.opensearch.index.BookIndex
import org.library.external.opensearch.dto.BookIndexDocument
import org.library.external.opensearch.dto.toSourceMap
import org.library.devtools.db.MysqlConfig
import org.library.devtools.db.OpenSearchConfig
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper

private val log = KotlinLogging.logger {}
private val objectMapper = JsonMapper.builder().build()

fun main() {
    OpenSearchConfig.newClient().use { client ->
        recreateIndex(client)

        val books = readBooksFromMysql()
        log.info { "MySQL에서 book ${books.size}건 조회 완료" }

        bulkIndex(client, books)
        client.performRequest(Request("POST", BookIndex.REFRESH_PATH))
        log.info { "OpenSearch '${BookIndex.NAME}' 인덱스에 ${books.size}건 색인 완료" }
    }
}

private fun recreateIndex(client: RestClient) {
    runCatching { client.performRequest(Request("DELETE", BookIndex.INDEX_PATH)) }
        .onFailure { log.info { "기존 인덱스 없음(정상): ${it.message}" } }

    val create = Request("PUT", BookIndex.INDEX_PATH)
    create.setJsonEntity(
        ClassPathResource(BookIndex.MAPPING_RESOURCE).inputStream.bufferedReader().use { it.readText() },
    )
    client.performRequest(create)
    log.info { "'${BookIndex.NAME}' 인덱스를 nori 매핑으로 재생성했습니다." }
}

private fun readBooksFromMysql(): List<BookIndexDocument> =
    MysqlConfig.jdbcTemplate.query(
        "SELECT id, title, author, publisher, isbn, created_at FROM book WHERE deleted_at IS NULL",
    ) { rs, _ ->
        BookIndexDocument(
            bookId = rs.getLong("id"),
            title = rs.getString("title"),
            author = rs.getString("author"),
            publisher = rs.getString("publisher"),
            isbn = rs.getString("isbn"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            version = 0L,
        )
    }

private fun bulkIndex(client: RestClient, books: List<BookIndexDocument>) {
    books.chunked(1000).forEachIndexed { chunkIndex, chunk ->
        val body = buildString {
            chunk.forEach { book ->
                append(
                    objectMapper.writeValueAsString(
                        mapOf("index" to mapOf("_index" to BookIndex.NAME, "_id" to book.bookId)),
                    ),
                )
                append('\n')
                append(objectMapper.writeValueAsString(book.toSourceMap()))
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
