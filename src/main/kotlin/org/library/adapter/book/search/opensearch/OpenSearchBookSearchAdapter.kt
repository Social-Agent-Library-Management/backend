package org.library.adapter.book.search.opensearch

import org.apache.http.util.EntityUtils
import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.bookitem.domain.BookItemRepository
import org.library.core.presentation.PageRequestParams
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class OpenSearchBookSearchAdapter(
    private val restClient: RestClient,
    private val bookItemRepository: BookItemRepository,
    private val objectMapper: ObjectMapper,
) : BookSearchPort {

    override fun search(query: String?, page: PageRequestParams): Page<BookDocument> {
        val pageRequest = page.toPageRequest()
        val keyword = query?.trim()?.takeIf { it.isNotBlank() }

        val queryClause = if (keyword == null) {
            """{ "match_all": {} }"""
        } else {
            """
            {
              "multi_match": {
                "query": ${objectMapper.writeValueAsString(keyword)},
                "fields": ["title", "author"],
                "fuzziness": "AUTO"
              }
            }
            """.trimIndent()
        }

        val requestBody = """
            {
              "from": ${pageRequest.offset},
              "size": ${pageRequest.pageSize},
              "track_total_hits": true,
              "query": $queryClause,
              "sort": [ { "createdAt": "desc" } ]
            }
        """.trimIndent()

        val request = Request("POST", "/books/_search")
        request.setJsonEntity(requestBody)
        val response = restClient.performRequest(request)
        val root = objectMapper.readTree(EntityUtils.toString(response.entity))

        val totalHits = root.path("hits").path("total").path("value").asLong(0)
        val hitNodes = root.path("hits").path("hits").toList()

        val bookIds = hitNodes.map { it.path("_source").path("bookId").asLong() }
        val bookItemCounts = if (bookIds.isEmpty()) {
            emptyMap()
        } else {
            bookItemRepository.countActiveByBookIdIn(bookIds).associate { it.bookId to it.itemCount }
        }

        val documents = hitNodes.map { hit ->
            val source = hit.path("_source")
            val id = source.path("bookId").asLong()
            BookDocument(
                id = id,
                title = source.path("title").asString(),
                author = source.path("author").asString(),
                publisher = source.path("publisher").asString(),
                isbn = source.path("isbn").takeIf { !it.isNull }?.asString(),
                bookItemCount = bookItemCounts[id] ?: 0L,
            )
        }

        return PageImpl(documents, pageRequest, totalHits)
    }
}
