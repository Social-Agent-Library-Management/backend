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

        // 검색 결과는 부분 문자열로 실재하는 것만 담으므로, 0건이 곧 "원문이 데이터에 없다"다.
        val suggestions = if (keyword == null || totalHits > 0L) emptyList() else suggestionsFor(keyword, root)

        return BookSearchResult(
            page = PageImpl(documents, pageRequest, totalHits),
            suggestions = suggestions,
        )
    }

    /**
     * 추천어는 성격이 다른 두 갈래에서 나온다. 둘은 서로 다른 실패를 메우므로 합쳐야 도달률이 나온다.
     *
     * - **suggester**: 글자 수는 맞고 글자가 틀린 통짜 오타를 담당한다(`"기역"` → `기억1`).
     *   자모 사전에서 후보를 뽑고 [resolveToRealTitle] 로 실재하는 제목까지 확정하므로 확신이 높다.
     *   그래서 맨 앞에 둔다.
     * - **순서 없는 ngram**: 앞부분만 치면서 오타까지 낸 입력을 담당한다(`"아투인문학"` → `아트인문학여행`).
     *   자모 편집거리가 6이라 suggester 가 포기하는 구간이고(`max_edits: 2`), 반대로 여기서는
     *   5글자 중 4글자가 맞아 걸린다.
     *
     * 확신이 낮은 쪽에 답을 하나만 들이대지 않으려고 [SUGGESTION_SIZE] 건까지 채운다. 사용자도
     * 앞 몇 자만 치고 훑어보는 상태라 후보 여러 개가 맞다.
     *
     * 마지막으로 **검색어 길이만큼 잘라낸다.** 두 갈래가 내놓는 건 전부 문서의 제목인데, 제목을
     * 그대로 제안하면 그걸 눌렀을 때 그 책 한 권만 나온다(`"기역"` → `기억1` → 1건). 사용자가
     * 원한 건 특정 책이 아니라 **고쳐진 검색어**다 — 2글자를 쳤으면 2글자짜리 검색어를 돌려줘야
     * `기억1`·`기억2`·`기억전쟁`이 함께 목록에 오른다(14건).
     *
     * 자를 위치는 [sliceAt] 이 정한다. suggester 쪽은 제안어가 제목 **한가운데** 있을 수 있어서
     * 맨 앞에서 자르면 엉뚱한 말이 나온다 — `"아투인"` 의 후보 `우주인` 은
     * `열한번째 도끼질 : … 최초 우주인 이소연의 …` 에 걸리므로 앞에서 3글자를 떼면 `열한번` 이 된다.
     * 순서 없는 조회 쪽은 제목 전체가 후보라 기준점이 없고, 사용자는 앞부터 치므로 맨 앞에서 자른다.
     *
     * 자른 문자열은 후보 제목의 부분 문자열이라 그 제목에 반드시 걸린다. 제안을 눌렀을 때 0건이
     * 되는 일이 구조적으로 없어서 검증 조회를 더 붙일 필요가 없다. 시리즈물이 접두사에서 하나로
     * 합쳐지는 것도 [distinct] 가 같이 처리한다(`아트인문학여행(스페인/이탈리아/파리)` → `아트인문학`).
     */
    private fun suggestionsFor(keyword: String, root: JsonNode): List<String> {
        val length = flatten(keyword).length
        val suggested = bestSuggestion(extractSuggestion(root, HangulJamo.decompose(keyword)), keyword)
        val corrected = suggested?.let { resolveToRealTitle(it.text) }
            ?.let { sliceAt(it, suggested.text, length) }
        return (listOfNotNull(corrected) + looseCandidates(keyword).map { sliceAt(it, null, length) })
            .filter { it.isNotBlank() }
            .distinct()
            .take(SUGGESTION_SIZE)
    }

    /**
     * [title] 에서 [anchor] 가 시작하는 자리부터 [length] 글자를 떼어낸다. [anchor] 가 null 이거나
     * 찾지 못하면 맨 앞에서 뗀다.
     *
     * 단순 `indexOf` 로는 못 찾는다. 제안어는 `title.jamo` 의 색인 term 이라 소문자에 문장부호가
     * 빠져 있고(`"wto체제와…"`), 제목 원문은 그렇지 않기 때문이다(`"WTO체제와 한국농업의 진로"`).
     * 그래서 글자·숫자만 남긴 사본에서 위치를 찾은 뒤 원문 인덱스로 되돌린다. 길이도 같은 기준으로
     * 세므로 중간에 낀 공백·문장부호는 글자 수에 포함되지 않는다.
     */
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

    /** `title.ngram` 이 무시하는 것들(공백·문장부호·대소문자)을 똑같이 지운 사본. */
    private fun flatten(text: String): String =
        text.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * `title.ngram` 을 **순서 없이** 조회해 추천어 후보를 받는다.
     *
     * 이 절은 원래 검색 쿼리의 `should` 에 `boost: 0.5` 로 들어 있었다. 오타가 있어도 정답을
     * 상위에 올려주긴 했지만, 검색 결과 목록에 **데이터에 없는 문자열의 추측**을 섞는 일이기도 했다
     * (`"아투인문학"` 은 어떤 제목에도 부분 문자열로 없다). 결과는 실재하는 것만, 추측은 추천어로
     * 분리하기로 하면서 별도 조회로 떼어냈다.
     *
     * 후처리로 걸러낼 수는 없었다. `from`·`size` 와 `hits.total` 은 OpenSearch 가 먼저 계산하므로
     * 어댑터에서 추측을 빼면 총건수가 부풀고 2페이지에 구멍이 뚫린다. 그래서 요청 자체를 나눴고,
     * 검색이 0건일 때만 나가므로 정상 검색에는 비용이 없다.
     */
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

    /**
     * suggester 는 실재하는 제목을 찾는 게 아니라 토큰을 조합한다. 각 토큰이 인덱스에 있어도
     * 그 조합은 아무 책도 아닐 수 있다(`"신에 대리인"` → `"신화 대리인"`). 게다가 조합된 문자열은
     * `title.jamo` 의 색인 term 이라 소문자로 내려오고(`"WTO…"` → `"wto…"`), 토크나이저가 버린
     * 쉼표·콜론도 복원되지 않는다.
     *
     * 그래서 제안어로 실제 문서를 한 번 찾아보고, 있으면 **그 문서의 원문 제목**을 대신 돌려준다.
     * `title.ngram` 은 공백·문장부호·대소문자를 모두 무시하므로 표기가 달라도 같은 책을 찾아내고,
     * 아무 문서도 못 찾으면 존재하지 않는 제안이므로 버린다.
     *
     * 다만 이 조회는 `match_phrase` 라 **부분 문자열로도 걸린다.** 제안어가 짧으면 그걸 품은 더 긴
     * 제목이 같이 잡히고, BM25 는 필드가 짧을수록 점수를 높게 주므로 `size: 1` 로는 엉뚱한 쪽이
     * 뽑힌다(`"나무도감"` → `중국대나무도감` 13.9 vs `나무도감(세밀화로 그린 큰도감)` 10.8).
     * 그래서 상위 몇 건을 받아 **제안어와 길이가 가장 가까운 제목**을 고른다. 표기 복원은 길이가
     * 거의 그대로라(`"wto체제와…"` → `"WTO체제와…"`) 이 규칙에 걸리지 않는다.
     *
     * 이 규칙은 [suggestionsFor] 가 결과를 검색어 길이로 자르기 때문에 더 중요해졌다. 엉뚱하게
     * `중국대나무도감` 을 골랐다면 4글자로 자를 때 `중국대나` 라는 쓸모없는 검색어가 나온다.
     */
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

    /**
     * 이 데이터는 제목 대부분이 띄어쓰기 없이 붙어 있어, `title.jamo` 의 term 이 "제목 전체"가 된다.
     * 그래서 사용자가 띄어 쓰면(`"강원기의 글쓰"`) 검색어가 조각나 통짜 제목과 편집거리가 멀어진다.
     * 후보가 아예 안 만들어지거나(`"강원긱의 글쓰기"` → 제안 없음), 토큰 하나만 엉뚱하게 고친
     * 제안이 나온다(`"강원기의 글쓰"` → `"강원기의 글로"`). 공백을 지우면 다시 통짜로 비교돼
     * 제대로 된 후보가 생긴다(`"강원국의글쓰기"`).
     *
     * 그래서 1차 제안이 있든 없든 재시도한 뒤, 언어모델 점수가 높은 쪽을 채택한다.
     * 검색이 0건일 때만 호출되므로 여기서 다시 건수를 볼 필요는 없다.
     */
    private fun bestSuggestion(primary: Suggestion?, keyword: String): Suggestion? {
        val stripped = keyword.replace(WHITESPACE, "")
        if (stripped == keyword || stripped.isBlank()) return primary

        val request = Request("POST", BookIndex.SEARCH_PATH)
        request.setJsonEntity("""{ ${suggestClause(stripped)} "size": 0 }""")
        val response = restClient.performRequest(request)
        val retried = extractSuggestion(objectMapper.readTree(EntityUtils.toString(response.entity)), HangulJamo.decompose(stripped))
            ?: return primary

        return if (primary == null || retried.score > primary.score) retried else primary
    }

    /**
     * 뒤에 콤마가 붙은 `"suggest": { … },` 조각. 요청 본문 조립과 재시도가 함께 쓴다.
     *
     * 설정이 같고 `confidence` 만 다른 suggester 두 개를 **한 요청에 나란히** 실어 보낸다. 엄격한 쪽이
     * 빈손일 때만 느슨한 쪽을 쓰므로(`extractSuggestion`), 왕복은 그대로 한 번이다.
     *
     * 느슨한 쪽이 필요한 건 띄어쓴 제목을 **붙여서** 입력하고 오타까지 낸 경우다. `"노치마과학"` 의 정답
     * `놓지마과학` 은 사전에 있지만(`jamo_glue` 가 붙인 형태를 만들어 둔다), 그 term 이 언어모델에는
     * 없어 백오프 바닥값을 받는다. 그래서 원문보다 점수가 낮고 `confidence: 1.0` 에서 탈락한다.
     * `confidence` 의 원래 역할("고칠 필요가 있나")은 이미 0건 게이트가 하고 있으므로 여기서는 풀어도 된다.
     */
    private fun suggestClause(keyword: String): String {
        val text = objectMapper.writeValueAsString(HangulJamo.decompose(keyword))
        return """
              "suggest": {
                "${BookIndex.TITLE_SUGGEST_NAME}": {
                  "text": $text, "phrase": ${phraseClause(SUGGEST_CONFIDENCE)}
                },
                "${BookIndex.TITLE_SUGGEST_LOOSE_NAME}": {
                  "text": $text, "phrase": ${phraseClause(SUGGEST_LOOSE_CONFIDENCE)}
                }
              },
        """.trimIndent()
    }

    private fun phraseClause(confidence: Double): String = """
        {
          "field": "${BookIndex.TITLE_JAMO_TRIGRAM_FIELD}",
          "gram_size": ${BookIndex.TITLE_SUGGEST_GRAM_SIZE},
          "confidence": $confidence,
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
    """.trimIndent()

    /** 어느 쪽 제안을 쓸지 점수로 비교해야 해서 text 와 score 를 같이 들고 다닌다. */
    private data class Suggestion(val text: String, val score: Double)

    /**
     * 후보는 자모 사전(`title.jamo`)에서 나오므로 **텍스트도 자모**로 내려온다
     * (`ㄱㅣㅇㅓㄱ`). 사람이 읽을 형태로 되돌려 돌려준다.
     *
     * `options[0]` 만 보면 안 된다. 언어모델은 **빈도**로 채점하는데 오타 교정에서 중요한 건
     * "원문과 얼마나 닮았나"다. 인덱스에 없는 문자열은 전부 같은 백오프 바닥값을 받아
     * 점수가 완전히 동점이 되기도 하고(`"나무와숨"` → `나무도감`·`나무와숲`), 흔한 단어가
     * 닮은 단어를 눌러버리기도 한다(`"기역"` → `기억`(freq 2) 대신 `기술`(freq 12)).
     *
     * 그래서 최고점의 [SUGGEST_SCORE_FLOOR_RATIO] 이상인 후보를 모아 편집거리로 다시 고른다.
     * 후보와 검색어가 이미 자모 문자열이라 이 거리가 곧 자모 거리다 — `기역→기억` 이 1,
     * `기역→기술` 이 3으로 갈린다(음절 단위로는 둘 다 1이라 구분되지 않는다).
     */
    private fun extractSuggestion(root: JsonNode, jamoText: String): Suggestion? =
        pickSuggestion(root, BookIndex.TITLE_SUGGEST_NAME, jamoText, dropOriginal = false)
            ?: pickSuggestion(root, BookIndex.TITLE_SUGGEST_LOOSE_NAME, jamoText, dropOriginal = true)

    /**
     * [dropOriginal] 은 느슨한 쪽에만 필요하다. `confidence: 1.0` 은 "원문보다 나은 후보"만 남기므로
     * 검색어 자신이 섞여 들어올 수 없지만, 풀어버리면 원문도 후보로 내려온다. 편집거리로 고르는 이상
     * 거리 0 인 원문이 무조건 이기므로, 사용자의 오타를 그대로 되돌려주게 된다.
     */
    private fun pickSuggestion(
        root: JsonNode,
        name: String,
        jamoText: String,
        dropOriginal: Boolean,
    ): Suggestion? {
        val options = root.path("suggest").path(name).path(0).path("options")
            .mapNotNull { option ->
                option.path("text").asString("")
                    .takeIf { it.isNotBlank() && !(dropOriginal && it == jamoText) }
                    ?.let { Suggestion(it, option.path("score").asDouble(0.0)) }
            }
        val scoreFloor = (options.maxOfOrNull { it.score } ?: return null) * SUGGEST_SCORE_FLOOR_RATIO
        return options.filter { it.score >= scoreFloor }
            .minWithOrNull(compareBy({ editDistance(jamoText, it.text) }, { -it.score }))
            ?.let { Suggestion(HangulJamo.compose(it.text), it.score) }
    }

    /** 동점 후보를 가를 때만 쓰는 레벤슈타인 거리. 제목 길이라 두 행 DP 로 충분하다. */
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

        /**
         * 추천어로 내려보낼 최대 건수.
         *
         * 검색 결과에서 추측을 걷어내면 부분입력·오타 사용자는 0건 화면을 보게 된다. 원래 검색
         * 상위권에서 정답을 만나던 경로(오타+부분입력 250건 중 227건)가 추천 영역으로 내려오는 것이라,
         * 거기서 하나만 보여주면 그 폭이 사라진다. 시스템도 확신이 없는 입력이므로 후보를 남겨둔다.
         */
        private const val SUGGESTION_SIZE = 3

        /**
         * 검색은 `title.ngram`·`author.ngram` 만 본다. 형태소 필드(`title`·`author`)를 조회하는
         * 절은 전부 뺐다.
         *
         * `title` 쪽은 nori 파편이 노이즈의 출처였다 — `쓰기` 가 `쓰`·`쓸` 로 풀리는 바람에
         * `"소셜쓰기의모"` 가 `역사의쓸모` 를 1위로 올렸다. `author` 쪽은 띄어쓰기가 다른 이름
         * (`"로버트 기요사키"` vs 색인된 `로버트기요사키`)을 잡아주는 값이 있었지만, 저자명 400건에서
         * 1위를 잃는 건 2건뿐이고 나머지 20건은 히트만 줄어드는(노이즈가 걷히는) 변화였다.
         *
         * 결과적으로 형태소 분석기가 틀렸던 게 아니라 **역할이 겹쳤다.** nori 를 넣은 이유였던
         * "권" 으로 `권력의종말` 찾기를 한 글자 ngram 이 더 정확하게 해내기 때문이다.
         *
         * 대가는 한글 독음으로 한자 제목을 찾는 경로다(`nori_readingform` 이 `造景`→`조경`).
         * 해당 제목이 192건(4.6%)이고, 한자를 직접 입력하면 `title.ngram` 이 그대로 찾아낸다.
         * `title`·`author` 필드 자체는 `suggest`·`trigram`·`ngram` 서브필드의 부모라 매핑에 남는다.
         */

        /**
         * [looseCandidates] 가 요구하는 글자 겹침 문턱. 3글자 이하면 전부, 4글자 이상이면
         * 80%(내림)를 요구한다.
         *
         * `match_phrase` 는 글자가 연속으로 붙어 있어야 해서 오타 하나에 통째로 깨진다. 그래서
         * "앞부분만 치면서 오타까지 낸" 검색어는 검색도 0건, 제안도 없이 끝났다
         * (`"아투인문학"` → `아트인문학여행` 과 편집거리 3이라 `max_edits: 2` 초과).
         * 순서를 풀면 5글자 중 4글자가 맞아 정답이 걸린다.
         *
         * 조건부가 아니면 짧은 검색어에서 무너진다. 고정 `"80%"` 는 2글자에 `floor(1.6)=1` 이라
         * OR 과 같아져서 `"기억"` 이 14 → 630건이 됐다. `"3<80%"` 면 15건으로 거의 그대로다.
         * 오타+부분입력 250건에서 정답 노출은 25 → 227건, 무관한 1위는 10 → 3건으로 오히려 줄었다.
         */
        private const val NGRAM_MIN_SHOULD_MATCH = "3<80%"

        /**
         * [looseCandidates] 를 시도할 최소 검색어 길이. 이보다 짧으면 조회하지 않는다.
         *
         * 순서를 푸는 순간 짧은 검색어는 "글자 몇 개가 아무 데나 있으면 매치"가 되어버린다.
         * 4장에서 0건으로 막아둔 `"강원대"` 가 `강원국글쓰기3부작1(대통령의글쓰기)` 에 걸리는 식이다.
         * 인덱스에 없는 3글자 조합 150건으로 재보니 가드가 없으면 41건(27%)에 결과가 생겼고,
         * 4자 이상으로 제한하니 0건이 됐다. `"아투인문학"`(5자)은 그대로 통과한다.
         *
         * 이제 이 조회는 검색이 아니라 추천어를 만들지만, 가드는 그대로 필요하다. 짧은 오타에
         * 아무 후보나 붙는 걸 막는 역할은 결과 목록이든 추천 목록이든 같기 때문이다.
         */
        private const val NGRAM_LOOSE_MIN_LENGTH = 4

        /**
         * 오타 교정의 탐색 폭. `direct_generator.size`(토큰당 후보 수)와
         * `phrase.size`(채점 중 유지할 경로 수)에 같이 쓴다.
         *
         * 역할이 다른 두 노브인데, 둘 다 기본값 5에서는 정답이 탐색 대상에서 빠진다
         * (`"신에 대리인"` 의 `신의` 가 후보 6위라 잘렸고, 경로 수가 5면 후보에 있어도
         * 상위 경로로 올라오지 못했다). **하나만 올리면 다른 쪽이 병목이라 효과가 0이다.**
         *
         * 오타 280건으로 5~40 구간을 훑어보니 정답 수만 보면 8부터 평평했지만(197 → 198건),
         * 자모 편집거리로 후보를 고르게 되면서 **채점 대상에 들어오는 것 자체가** 중요해졌다.
         * `"기역"` 의 정답 `기억` 은 빈도가 낮아 후보 9위라 8에서 잘린다. 15 로 올리면 들어오고,
         * 그 뒤 자모 거리(1 대 3)로 `기술` 을 이긴다. 지연은 중앙값 3.83 → 4.14ms 로 차이가 없다.
         */
        private const val SUGGEST_SEARCH_WIDTH = 15

        /**
         * 자모 편집거리로 다시 줄 세울 후보의 하한. 최고점의 이 비율 이상이면 후보로 남긴다.
         *
         * 1.0(동점만) → 0.8 → 0.5 로 낮춰가며 오타 670건을 재보니 0.5 에서 정답이 가장 많았고
         * (489 → 495건) **악화는 0건**이었다. 더 낮추면 언어모델을 사실상 무시하게 되므로
         * 절반을 하한으로 둔다.
         */
        private const val SUGGEST_SCORE_FLOOR_RATIO = 0.5

        /** 제안어를 실제 제목으로 되돌릴 때 길이를 비교해볼 후보 수. */
        private const val RESOLVE_CANDIDATES = 5

        /**
         * 후보가 원본보다 나아야 하는 배수. 1.0 이면 "원본보다 나은 후보만".
         *
         * 원래는 정상 검색어에 엉뚱한 제안이 붙는 걸 막는 장치였지만(`"조경"` → `조건?`), 지금은
         * 검색이 0건일 때만 제안을 만들므로 그 역할이 없다. 그래도 기본값으로 남겨둔다 — 후보가
         * 여럿일 때 언어모델이 걸러주는 쪽이 정확도가 높기 때문이다. 오타 패턴별 도달률에서
         * 이 값을 아예 0 으로 내리면 "앞 6자 + 첫음절 오타"가 98.9% → 96.8% 로 떨어졌다.
         */
        private const val SUGGEST_CONFIDENCE = 1.0

        /** 엄격한 쪽이 빈손일 때 쓰는 두 번째 suggester 의 `confidence`. 사실상 끄는 값이다. */
        private const val SUGGEST_LOOSE_CONFIDENCE = 0.0


        private val WHITESPACE = Regex("\\s+")
    }
}
