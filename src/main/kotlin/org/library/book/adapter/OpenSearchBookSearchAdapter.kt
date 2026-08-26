package org.library.book.adapter

import org.apache.http.util.EntityUtils
import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.book.application.port.BookSearchResult
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.countActiveItemsByBookId
import org.library.core.presentation.PageRequestParams
import org.library.external.opensearch.index.BookIndex
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class OpenSearchBookSearchAdapter(
    private val restClient: RestClient,
    private val bookItemRepository: BookItemRepository,
    private val objectMapper: ObjectMapper,
) : BookSearchPort {

    override fun search(query: String?, page: PageRequestParams): BookSearchResult {
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

        val suggestField = keyword?.let {
            """
              "suggest": {
                "${BookIndex.TITLE_SUGGEST_NAME}": {
                  "text": ${objectMapper.writeValueAsString(it)},
                  "phrase": {
                    "field": "${BookIndex.TITLE_TRIGRAM_FIELD}",
                    "gram_size": ${BookIndex.TITLE_SUGGEST_GRAM_SIZE},
                    "confidence": 1.0,
                    "max_errors": 2,
                    "direct_generator": [
                      {
                        "field": "${BookIndex.TITLE_SUGGEST_FIELD}",
                        "suggest_mode": "always",
                        "min_word_length": 1,
                        "prefix_length": 1,
                        "max_edits": 2
                      }
                    ]
                  }
                }
              },
            """.trimIndent()
        }.orEmpty()

        val requestBody = """
            {
              $suggestField
              "from": ${pageRequest.offset},
              "size": ${pageRequest.pageSize},
              "track_total_hits": true,
              "query": $queryClause,
              "sort": [ { "createdAt": "desc" } ]
            }
        """.trimIndent()

        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity(requestBody)
        val response = restClient.performRequest(request)
        val root = objectMapper.readTree(EntityUtils.toString(response.entity))

        val totalHits = root.path("hits").path("total").path("value").asLong(0)
        val hitNodes = root.path("hits").path("hits").toList()

        val bookIds = hitNodes.map { it.path("_source").path("bookId").asLong() }
        val bookItemCounts = bookItemRepository.countActiveItemsByBookId(bookIds)

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

        return BookSearchResult(
            page = PageImpl(documents, pageRequest, totalHits),
            suggestion = extractSuggestion(root),
        )
    }

    private fun extractSuggestion(root: JsonNode): String? =
        root.path("suggest").path(BookIndex.TITLE_SUGGEST_NAME).path(0).path("options").path(0).path("text")
            .asString("")
            .takeIf { it.isNotBlank() }
}
