package org.library.devtools.benchmark

import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.library.devtools.db.MysqlConfig
import org.library.devtools.db.OpenSearchConfig
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import tools.jackson.databind.json.JsonMapper

// BookRepository.searchActive와 의미적으로 동일한 LIKE 검색.
private const val LIKE_SQL = """
    SELECT id FROM book
    WHERE deleted_at IS NULL
      AND (LOWER(title) LIKE LOWER(CONCAT('%', ?, '%')) OR LOWER(author) LIKE LOWER(CONCAT('%', ?, '%')))
    ORDER BY created_at DESC LIMIT 20
"""

// BookRepository.searchFullText(FullTextBookSearchAdapter)와 동일한 FULLTEXT(ngram) 검색.
private const val FULLTEXT_SQL = """
    SELECT id FROM (
        SELECT id, MATCH(title, author) AGAINST(? IN NATURAL LANGUAGE MODE) AS score
        FROM book
        WHERE deleted_at IS NULL
          AND MATCH(title, author) AGAINST(? IN NATURAL LANGUAGE MODE)
    ) t
    WHERE score > 0.0001
    ORDER BY score DESC
    LIMIT 20
"""

private data class KeywordCase(val label: String, val keyword: String, val note: String)

private val cases = listOf(
    KeywordCase("흔한 키워드", "스프링", "다수 매칭 예상 (tier S)"),
    KeywordCase("희귀 키워드", "미니멀리즘 실천법", "소수 건만 매칭 예상 (tier Rare)"),
    KeywordCase("접두 검색", "클린", "'클린 코드'/'클린 아키텍처' 등 접두 매칭"),
    KeywordCase("형태소 경계를 넘는 부분 문자열", "린코", "'클린 코드'의 어중간한 substring, 단어 경계 무시"),
    KeywordCase("오타(fuzzy)", "스프릥", "'스프링'의 1글자 오타"),
)

private data class MysqlResult(val tree: String, val totalMs: Double, val rows: Int)
private data class OpenSearchResult(val ms: Double, val hits: Long)

// 트리 최상단(outermost) 연산자 줄의 `actual time=X..Y rows=N loops=M`에서
// Y(마지막 행까지 걸린 시간)와 N(실제 처리 행수)을 뽑는다.
private val timingPattern = Regex("""actual time=[\d.]+\.\.([\d.]+) rows=(\d+) loops=\d+""")

private val objectMapper = JsonMapper.builder().build()

/**
 * IDE에서 `fun main()`을 직접 실행. LIKE(MySQL 풀스캔) / FULLTEXT(MySQL ngram 역색인) /
 * OpenSearch(nori 형태소 분석 + fuzziness) 세 엔진을 같은 키워드 케이스로 한 번에 비교한다.
 * MySQL 쪽은 `EXPLAIN ANALYZE`로 실제 실행시켜 서버 내부 실측 시간을 뽑고, OpenSearch 쪽은
 * `_search` 응답의 `took`(서버 내부 실측 ms)을 그대로 쓴다 — 네트워크/API 오버헤드는 제외한
 * "엔진 자체가 얼마나 빠른가"를 같은 기준으로 비교하기 위함.
 */
fun main() {
    val likeResults = cases.map { it to runMysqlExplainAnalyze(LIKE_SQL, it.keyword) }
    val ftResults = cases.map { it to runMysqlExplainAnalyze(FULLTEXT_SQL, it.keyword) }

    // RestClient는 내부에 비-데몬 I/O 스레드를 띄우므로, 닫지 않으면 JVM이 종료되지 않고
    // System.out 버퍼도 끝까지 flush되지 않는다(process가 안 끝나서 출력이 파일에 안 찍힘) — use{}로 확실히 닫는다.
    val osResults = OpenSearchConfig.newClient().use { client ->
        cases.map { it to runOpenSearch(client, it.keyword) }
    }

    println("## 실행계획 원본 (MySQL EXPLAIN ANALYZE)\n")
    printTrees("LIKE 검색 (BookRepository.searchActive와 동일)", likeResults)
    printTrees("FULLTEXT(ngram) 검색 (BookRepository.searchFullText와 동일)", ftResults)

    println("## 3-way 실측 응답시간 비교 (엔진 서버 내부 실행시간, 네트워크/API 오버헤드 제외)\n")
    println("| 케이스 | 키워드 | LIKE(ms) | rows | FULLTEXT(ms) | rows | OpenSearch(ms) | hits | 비고 |")
    println("|---|---|---|---|---|---|---|---|---|")
    cases.forEachIndexed { i, case ->
        val like = likeResults[i].second
        val ft = ftResults[i].second
        val os = osResults[i].second
        println(
            "| %s | %s | %.2f | %d | %.2f | %d | %.2f | %d | %s |".format(
                case.label, case.keyword, like.totalMs, like.rows, ft.totalMs, ft.rows, os.ms, os.hits, case.note,
            ),
        )
    }
}

private fun printTrees(title: String, results: List<Pair<KeywordCase, MysqlResult>>) {
    println("### $title\n")
    results.forEach { (case, result) ->
        println("**${case.label} (\"${case.keyword}\")**")
        println("```")
        println(result.tree.trim())
        println("```\n")
    }
}

private fun runMysqlExplainAnalyze(sql: String, keyword: String): MysqlResult {
    val argCount = sql.count { it == '?' }
    val args = Array(argCount) { keyword as Any }
    val tree = MysqlConfig.jdbcTemplate.queryForObject("EXPLAIN ANALYZE $sql", String::class.java, *args).orEmpty()
    val match = timingPattern.find(tree)
    val totalMs = match?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
    val rows = match?.groupValues?.get(2)?.toIntOrNull() ?: -1
    return MysqlResult(tree, totalMs, rows)
}

private fun runOpenSearch(client: RestClient, keyword: String): OpenSearchResult {
    val body = """
        {
          "size": 20,
          "track_total_hits": true,
          "query": {
            "multi_match": {
              "query": ${objectMapper.writeValueAsString(keyword)},
              "fields": ["title", "author"],
              "fuzziness": "AUTO"
            }
          }
        }
    """.trimIndent()

    val request = Request("POST", "/books/_search")
    request.setEntity(StringEntity(body, ContentType.create("application/json", Charsets.UTF_8)))
    val response = client.performRequest(request)
    val root = objectMapper.readTree(EntityUtils.toString(response.entity))

    val took = root.path("took").asLong(0).toDouble()
    val hits = root.path("hits").path("total").path("value").asLong(0)
    return OpenSearchResult(took, hits)
}
