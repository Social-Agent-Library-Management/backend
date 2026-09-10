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
            val escaped = objectMapper.writeValueAsString(keyword)
            """
            {
              "bool": {
                "minimum_should_match": 1,
                "should": [
                  {
                    "multi_match": {
                      "query": $escaped,
                      "type": "best_fields",
                      "fields": ["title^3", "author^2"],
                      "minimum_should_match": "$MORPHEME_MIN_SHOULD_MATCH"
                    }
                  },
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

        // 검색어 원문이 어떤 제목·저자에도 부분 문자열로 없을 때만 오타로 보고 교정을 제안한다.
        val exactSubstringAgg = keyword?.let {
            val escaped = objectMapper.writeValueAsString(it)
            """
              "aggs": {
                "$EXACT_SUBSTRING_AGG": {
                  "filter": {
                    "bool": {
                      "minimum_should_match": 1,
                      "should": [
                        { "match_phrase": { "${BookIndex.TITLE_NGRAM_FIELD}": $escaped } },
                        { "match_phrase": { "${BookIndex.AUTHOR_NGRAM_FIELD}": $escaped } }
                      ]
                    }
                  }
                }
              },
            """.trimIndent()
        }.orEmpty()

        val requestBody = """
            {
              $suggestField
              $exactSubstringAgg
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

        val exactSubstringHits =
            root.path("aggregations").path(EXACT_SUBSTRING_AGG).path("doc_count").asLong(0)

        val primary = extractSuggestion(root).takeIf { exactSubstringHits == 0L }
        val suggestion = bestSuggestion(primary, keyword, totalHits, exactSubstringHits)
            ?.let { resolveToRealTitle(it.text) }

        return BookSearchResult(
            page = PageImpl(documents, pageRequest, totalHits),
            suggestion = suggestion,
        )
    }

    /**
     * suggester 는 실재하는 제목을 찾는 게 아니라 토큰을 조합한다. 각 토큰이 인덱스에 있어도
     * 그 조합은 아무 책도 아닐 수 있다(`"신에 대리인"` → `"신화 대리인"`). 게다가 조합된 문자열은
     * `title.suggest` 의 색인 term 이라 소문자로 내려오고(`"WTO…"` → `"wto…"`), 토크나이저가 버린
     * 쉼표·콜론도 복원되지 않는다.
     *
     * 그래서 제안어로 실제 문서를 한 번 찾아보고, 있으면 **그 문서의 원문 제목**을 대신 돌려준다.
     * `title.ngram` 은 공백·문장부호·대소문자를 모두 무시하므로 표기가 달라도 같은 책을 찾아내고,
     * 아무 문서도 못 찾으면 존재하지 않는 제안이므로 버린다.
     */
    private fun resolveToRealTitle(suggestion: String): String? {
        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity(
            """
            {
              "size": 1,
              "_source": ["title"],
              "query": { "match_phrase": { "${BookIndex.TITLE_NGRAM_FIELD}": ${objectMapper.writeValueAsString(suggestion)} } }
            }
            """.trimIndent(),
        )
        val response = restClient.performRequest(request)
        val root = objectMapper.readTree(EntityUtils.toString(response.entity))
        return root.path("hits").path("hits").path(0).path("_source").path("title")
            .asString("")
            .takeIf { it.isNotBlank() }
    }

    /**
     * 이 데이터는 제목 대부분이 띄어쓰기 없이 붙어 있어, `title.suggest` 의 term 이 "제목 전체"가 된다.
     * 그래서 사용자가 띄어 쓰면(`"강원기의 글쓰"`) 검색어가 조각나 통짜 제목과 편집거리가 멀어진다.
     * 후보가 아예 안 만들어지거나(`"강원긱의 글쓰기"` → 제안 없음), 토큰 하나만 엉뚱하게 고친
     * 제안이 나온다(`"강원기의 글쓰"` → `"강원기의 글로"`). 공백을 지우면 다시 통짜로 비교돼
     * 제대로 된 후보가 생긴다(`"강원국의글쓰기"`).
     *
     * 그래서 1차 제안이 있든 없든 재시도한 뒤, 언어모델 점수가 높은 쪽을 채택한다.
     * 결과가 하나라도 있으면 재시도하지 않는다 — 어순만 뒤바꿔 입력한 경우(결과는 있으나
     * 원문이 부분 문자열로 없는 경우)에 엉뚱한 제안이 붙는 걸 막기 위한 조건이다.
     */
    private fun bestSuggestion(
        primary: Suggestion?,
        keyword: String?,
        totalHits: Long,
        exactSubstringHits: Long,
    ): Suggestion? {
        if (keyword == null || totalHits > 0L || exactSubstringHits > 0L) return primary
        val stripped = keyword.replace(WHITESPACE, "")
        if (stripped == keyword || stripped.isBlank()) return primary

        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity("""{ ${suggestClause(stripped)} "size": 0 }""")
        val response = restClient.performRequest(request)
        val retried = extractSuggestion(objectMapper.readTree(EntityUtils.toString(response.entity)))
            ?: return primary

        return if (primary == null || retried.score > primary.score) retried else primary
    }

    /** 뒤에 콤마가 붙은 `"suggest": { … },` 조각. 요청 본문 조립과 재시도가 함께 쓴다. */
    private fun suggestClause(keyword: String): String = """
          "suggest": {
            "${BookIndex.TITLE_SUGGEST_NAME}": {
              "text": ${objectMapper.writeValueAsString(keyword)},
              "phrase": {
                "field": "${BookIndex.TITLE_TRIGRAM_FIELD}",
                "gram_size": ${BookIndex.TITLE_SUGGEST_GRAM_SIZE},
                "confidence": 1.0,
                "max_errors": 2,
                "size": $SUGGEST_SEARCH_WIDTH,
                "direct_generator": [
                  {
                    "field": "${BookIndex.TITLE_SUGGEST_FIELD}",
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

    /** 어느 쪽 제안을 쓸지 점수로 비교해야 해서 text 와 score 를 같이 들고 다닌다. */
    private data class Suggestion(val text: String, val score: Double)

    private fun extractSuggestion(root: JsonNode): Suggestion? {
        val option = root.path("suggest").path(BookIndex.TITLE_SUGGEST_NAME).path(0).path("options").path(0)
        val text = option.path("text").asString("").takeIf { it.isNotBlank() } ?: return null
        return Suggestion(text, option.path("score").asDouble(0.0))
    }

    companion object {
        private const val EXACT_SUBSTRING_AGG = "exact_substring"

        /**
         * 토큰 2개 이하면 전부 일치를 요구하고, 3개 이상이면 75%(내림)만 요구한다.
         *
         * "강원대" → [강원, 대] 처럼 두 토큰 중 하나가 인덱스에 없으면 남은 한 토큰이 결과를
         * 지배해버린다("대" 하나로 24건). 짧은 검색어를 엄격하게 막으면서, 토큰이 많은
         * 검색어는 일부가 어긋나도 나머지가 서로를 검증하도록 관대하게 둔다.
         */
        private const val MORPHEME_MIN_SHOULD_MATCH = "2<75%"

        /**
         * 오타 교정의 탐색 폭. `direct_generator.size`(토큰당 후보 수)와
         * `phrase.size`(채점 중 유지할 경로 수)에 같이 쓴다.
         *
         * 역할이 다른 두 노브인데, 둘 다 기본값 5에서는 정답이 탐색 대상에서 빠진다
         * (`"신에 대리인"` 의 `신의` 가 후보 6위라 잘렸고, 경로 수가 5면 후보에 있어도
         * 상위 경로로 올라오지 못했다). **하나만 올리면 다른 쪽이 병목이라 효과가 0이다.**
         *
         * 오타 280건으로 5~40 구간을 훑어보니 둘 다 8부터 평평해져(정답 197 → 198건)
         * 그 이상은 측정 가능한 차이가 없었다. 어휘가 커지면 탐색 비용이 따라 커지므로
         * 넉넉한 값을 남기지 않고 충분한 최소값을 쓴다. 한쪽만 조정할 일이 생기면 분리한다.
         */
        private const val SUGGEST_SEARCH_WIDTH = 8

        private val WHITESPACE = Regex("\\s+")
    }
}
