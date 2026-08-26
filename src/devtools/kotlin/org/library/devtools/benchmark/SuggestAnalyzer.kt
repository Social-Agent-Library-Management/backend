package org.library.devtools.benchmark

import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.library.devtools.db.OpenSearchConfig
import org.library.external.opensearch.index.BookIndex
import org.opensearch.client.Request
import org.opensearch.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private val objectMapper = JsonMapper.builder().build()

private data class SuggestOption(val text: String, val highlighted: String, val score: Double)
private data class SearchResult(val hits: Long, val topTitles: List<String>, val tookMs: Long)
private data class CaseReport(
    val case: KeywordCase,
    val before: SearchResult,
    val options: List<SuggestOption>,
    val after: SearchResult?,
)

fun main() {
    val reports = OpenSearchConfig.newClient().use { client ->
        KeywordCases.all.map { case ->
            val (before, options) = searchWithSuggest(client, case.keyword)
            val after = options.firstOrNull()?.let { searchOnly(client, it.text) }
            CaseReport(case, before, options, after)
        }
    }

    printSuggestDetails(reports)
    printSummaryTable(reports)
}

private fun searchWithSuggest(client: RestClient, keyword: String): Pair<SearchResult, List<SuggestOption>> {
    val quoted = objectMapper.writeValueAsString(keyword)
    val body = """
        {
          "size": 3,
          "track_total_hits": true,
          "query": {
            "multi_match": {
              "query": $quoted,
              "fields": ["title", "author"],
              "fuzziness": "AUTO"
            }
          },
          "suggest": {
            "title_suggest": {
              "text": $quoted,
              "phrase": {
                "field": "title.trigram",
                "size": 3,
                "gram_size": ${BookIndex.TITLE_SUGGEST_GRAM_SIZE},
                "confidence": 1.0,
                "max_errors": 2,
                "direct_generator": [
                  {
                    "field": "title.suggest",
                    "suggest_mode": "always",
                    "min_word_length": 1,
                    "prefix_length": 1,
                    "max_edits": 2
                  }
                ],
                "highlight": { "pre_tag": "<em>", "post_tag": "</em>" }
              }
            }
          }
        }
    """.trimIndent()

    val root = post(client, body)
    return toSearchResult(root) to toSuggestOptions(root)
}

private fun searchOnly(client: RestClient, keyword: String): SearchResult {
    val body = """
        {
          "size": 3,
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
    return toSearchResult(post(client, body))
}

private fun post(client: RestClient, body: String): JsonNode {
    val request = Request("POST", BookIndex.SEARCH_PATH)
    request.setEntity(StringEntity(body, ContentType.create("application/json", Charsets.UTF_8)))
    val response = client.performRequest(request)
    return objectMapper.readTree(EntityUtils.toString(response.entity))
}

private fun toSearchResult(root: JsonNode): SearchResult = SearchResult(
    hits = root.path("hits").path("total").path("value").asLong(0),
    topTitles = root.path("hits").path("hits").toList().map { it.path("_source").path("title").asString("") },
    tookMs = root.path("took").asLong(0),
)

private fun toSuggestOptions(root: JsonNode): List<SuggestOption> =
    root.path("suggest").path("title_suggest").path(0).path("options").toList().map {
        SuggestOption(
            text = it.path("text").asString(""),
            highlighted = it.path("highlighted").asString(""),
            score = it.path("score").asDouble(0.0),
        )
    }

private fun printSuggestDetails(reports: List<CaseReport>) {
    println("## Phrase Suggester 원본 응답 (suggest.title_suggest[0].options)\n")
    reports.forEach { report ->
        println("**${report.case.label} (\"${report.case.keyword}\")**")
        println("```")
        if (report.options.isEmpty()) {
            println("(제안 없음 — confidence 기준을 넘는 후보가 없다 = 보정할 필요가 없다고 판단)")
        } else {
            report.options.forEach { println("${it.text}  score=%.8f  ${it.highlighted}".format(it.score)) }
        }
        report.after?.let { println("보정 후 상위 ${it.topTitles.size}건: ${it.topTitles.joinToString(" / ")}") }
        println("```\n")
    }
}

private fun printSummaryTable(reports: List<CaseReport>) {
    println("## 오타 보정 전/후 매칭 건수 비교\n")
    println("| 케이스 | 키워드 | 제안어 | score | 보정 전 hits | 보정 후 hits | 비고 |")
    println("|---|---|---|---|---|---|---|")
    reports.forEach { report ->
        val top = report.options.firstOrNull()
        println(
            "| %s | %s | %s | %s | %d | %s | %s |".format(
                report.case.label,
                report.case.keyword,
                top?.text ?: "-",
                top?.let { "%.8f".format(it.score) } ?: "-",
                report.before.hits,
                report.after?.hits?.toString() ?: "-",
                report.case.note,
            ),
        )
    }
}
