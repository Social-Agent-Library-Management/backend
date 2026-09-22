package org.library.book.adapter

import org.apache.http.util.EntityUtils
import org.library.book.application.port.BookDocument
import org.library.book.application.port.BookSearchPort
import org.library.book.application.port.BookSearchResult
import org.library.bookitem.domain.BookItemRepository
import org.library.bookitem.domain.countActiveItemsByBookId
import org.library.core.presentation.PageRequestParams
import org.library.external.opensearch.index.BookIndex
import org.library.external.opensearch.index.HangulJamo
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.math.abs

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
            val escaped = objectMapper.writeValueAsString(keyword)
            """
            {
              "bool": {
                "minimum_should_match": 1,
                "should": [
                  {
                    "match_phrase": {
                      "${BookIndex.TITLE_NGRAM_FIELD}": { "query": $escaped, "boost": 2 }
                    }
                  },
                  {
                    "match_phrase": {
                      "${BookIndex.AUTHOR_NGRAM_FIELD}": { "query": $escaped, "boost": 1 }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        }

        val suggestField = keyword?.let { suggestClause(it) }.orEmpty()

        val requestBody = """
            {
              $suggestField
              "from": ${pageRequest.offset},
              "size": ${pageRequest.pageSize},
              "track_total_hits": true,
              "query": $queryClause,
              "sort": [ "_score", { "createdAt": "desc" }, { "bookId": "desc" } ]
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

        val suggestions = if (keyword == null || totalHits > 0L) emptyList() else suggestionsFor(keyword, root)

        return BookSearchResult(
            page = PageImpl(documents, pageRequest, totalHits),
            suggestions = suggestions,
        )
    }

    private fun suggestionsFor(keyword: String, root: JsonNode): List<String> {
        val length = flatten(keyword).length
        val corrected = bestSuggestions(extractSuggestions(root, HangulJamo.decompose(keyword), keyword), keyword)
            .take(SUGGESTION_SIZE)
            .mapNotNull { candidate ->
                resolveToRealTitle(candidate.text)?.let { sliceAt(it, candidate.text, length) }
            }
            .filter { it.isNotBlank() }
            .distinct()

        return corrected.ifEmpty {
            looseCandidates(keyword)
                .map { sliceAt(it, null, length) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(SUGGESTION_SIZE)
        }
    }

    private fun sliceAt(title: String, anchor: String?, length: Int): String {
        val positions = ArrayList<Int>(title.length)
        val flat = buildString {
            title.forEachIndexed { at, char ->
                if (char.isLetterOrDigit()) {
                    append(char.lowercaseChar())
                    positions += at
                }
            }
        }
        if (positions.isEmpty() || length <= 0) return title

        val start = anchor?.let { flat.indexOf(flatten(it)) }?.takeIf { it >= 0 } ?: 0
        val last = start + length - 1
        return if (last >= positions.size) {
            title.substring(positions[start])
        } else {
            title.substring(positions[start], positions[last] + 1)
        }
    }

    private fun flatten(text: String): String =
        text.filter { it.isLetterOrDigit() }.lowercase()

    private fun looseCandidates(keyword: String): List<String> {
        if (keyword.length < NGRAM_LOOSE_MIN_LENGTH) return emptyList()

        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity(
            """
            {
              "size": $SUGGESTION_SIZE,
              "_source": ["title"],
              "query": {
                "match": {
                  "${BookIndex.TITLE_NGRAM_FIELD}": {
                    "query": ${objectMapper.writeValueAsString(keyword)},
                    "minimum_should_match": "$NGRAM_MIN_SHOULD_MATCH"
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val response = restClient.performRequest(request)
        return objectMapper.readTree(EntityUtils.toString(response.entity))
            .path("hits").path("hits")
            .mapNotNull { it.path("_source").path("title").asString("").takeIf { title -> title.isNotBlank() } }
    }

    private fun resolveToRealTitle(suggestion: String): String? {
        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity(
            """
            {
              "size": $RESOLVE_CANDIDATES,
              "_source": ["title"],
              "query": { "match_phrase": { "${BookIndex.TITLE_NGRAM_FIELD}": ${objectMapper.writeValueAsString(suggestion)} } }
            }
            """.trimIndent(),
        )
        val response = restClient.performRequest(request)
        val root = objectMapper.readTree(EntityUtils.toString(response.entity))
        return root.path("hits").path("hits")
            .mapNotNull { it.path("_source").path("title").asString("").takeIf { title -> title.isNotBlank() } }
            .minByOrNull { abs(it.length - suggestion.length) }
    }

    private fun bestSuggestions(primary: List<Suggestion>, keyword: String): List<Suggestion> {
        val stripped = keyword.replace(WHITESPACE, "")
        if (stripped == keyword || stripped.isBlank()) return primary

        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity("""{ ${suggestClause(stripped)} "size": 0 }""")
        val response = restClient.performRequest(request)
        val retried = extractSuggestions(
            objectMapper.readTree(EntityUtils.toString(response.entity)),
            HangulJamo.decompose(stripped),
            keyword,
        )
        if (retried.isEmpty()) return primary

        return if (primary.isEmpty() || retried.first().score > primary.first().score) retried else primary
    }

    private fun suggestClause(keyword: String): String = """
          "suggest": {
            "${BookIndex.TITLE_SUGGEST_NAME}": {
              "text": ${objectMapper.writeValueAsString(HangulJamo.decompose(keyword))},
              "phrase": {
                "field": "${BookIndex.TITLE_JAMO_TRIGRAM_FIELD}",
                "gram_size": ${BookIndex.TITLE_SUGGEST_GRAM_SIZE},
                "confidence": $SUGGEST_CONFIDENCE,
                "max_errors": 2,
                "size": $SUGGEST_SEARCH_WIDTH,
                "direct_generator": [
                  {
                    "field": "${BookIndex.TITLE_JAMO_FIELD}",
                    "suggest_mode": "always",
                    "min_word_length": 1,
                    "prefix_length": 1,
                    "max_edits": 2,
                    "size": $SUGGEST_SEARCH_WIDTH
                  }
                ]
              }
            }
          },
    """.trimIndent()

    private data class Suggestion(val text: String, val score: Double)

    private fun extractSuggestions(root: JsonNode, jamoText: String, keyword: String): List<Suggestion> {
        val typed = flatten(keyword)
        val options = root.path("suggest").path(BookIndex.TITLE_SUGGEST_NAME).path(0).path("options")
            .mapNotNull { option ->
                option.path("text").asString("").takeIf { it.isNotBlank() }
                    ?.let { Suggestion(it, option.path("score").asDouble(0.0)) }
            }
            .filter { flatten(HangulJamo.compose(it.text)) != typed }
        val scoreFloor = (options.maxOfOrNull { it.score } ?: return emptyList()) * SUGGEST_SCORE_FLOOR_RATIO
        return options.filter { it.score >= scoreFloor }
            .sortedWith(compareBy({ editDistance(jamoText, it.text) }, { -it.score }))
            .map { Suggestion(HangulJamo.compose(it.text), it.score) }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val substitution = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, substitution)
            }
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[b.length]
    }

    companion object {

        private const val SUGGESTION_SIZE = 3
        private const val NGRAM_MIN_SHOULD_MATCH = "3<80%"
        private const val NGRAM_LOOSE_MIN_LENGTH = 4
        private const val SUGGEST_SEARCH_WIDTH = 15
        private const val SUGGEST_SCORE_FLOOR_RATIO = 0.5
        private const val RESOLVE_CANDIDATES = 5
        private const val SUGGEST_CONFIDENCE = 0.9

        private val WHITESPACE = Regex("\\s+")
    }
}
