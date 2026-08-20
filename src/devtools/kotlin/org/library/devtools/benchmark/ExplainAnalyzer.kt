package org.library.devtools.benchmark

import org.library.book.domain.BookRepository.Companion.FULLTEXT_MIN_SCORE
import org.library.devtools.db.MysqlConfig

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
    WHERE score > $FULLTEXT_MIN_SCORE
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

private data class AnalyzeResult(val tree: String, val totalMs: Double, val rows: Int)

private val timingPattern = Regex("""actual time=[\d.]+\.\.([\d.]+) rows=(\d+) loops=\d+""")

fun main() {
    val likeResults = cases.map { it to runExplainAnalyze(LIKE_SQL, it.keyword) }
    val ftResults = cases.map { it to runExplainAnalyze(FULLTEXT_SQL, it.keyword) }

    println("## 실행계획 원본 (EXPLAIN ANALYZE)\n")
    printTrees("LIKE 검색 (BookRepository.searchActive와 동일)", likeResults)
    printTrees("FULLTEXT(ngram) 검색 (BookRepository.searchFullText와 동일)", ftResults)

    println("## 실측 응답시간 요약 (MySQL 서버 내부 실행시간, 네트워크/API 오버헤드 제외)\n")
    println("| 케이스 | 키워드 | LIKE(ms) | LIKE rows | FULLTEXT(ms) | FT rows | 배율 | 비고 |")
    println("|---|---|---|---|---|---|---|---|")
    cases.forEachIndexed { i, case ->
        val like = likeResults[i].second
        val ft = ftResults[i].second
        val speedup = if (ft.totalMs > 0) "%.1fx".format(like.totalMs / ft.totalMs) else "-"
        println(
            "| %s | %s | %.2f | %d | %.2f | %d | %s | %s |".format(
                case.label, case.keyword, like.totalMs, like.rows, ft.totalMs, ft.rows, speedup, case.note,
            ),
        )
    }
}

private fun printTrees(title: String, results: List<Pair<KeywordCase, AnalyzeResult>>) {
    println("### $title\n")
    results.forEach { (case, result) ->
        println("**${case.label} (\"${case.keyword}\")**")
        println("```")
        println(result.tree.trim())
        println("```\n")
    }
}

private fun runExplainAnalyze(sql: String, keyword: String): AnalyzeResult {
    val argCount = sql.count { it == '?' }
    val args = Array(argCount) { keyword as Any }
    val tree = MysqlConfig.jdbcTemplate.queryForObject("EXPLAIN ANALYZE $sql", String::class.java, *args).orEmpty()
    val match = timingPattern.find(tree)
    val totalMs = match?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
    val rows = match?.groupValues?.get(2)?.toIntOrNull() ?: -1
    return AnalyzeResult(tree, totalMs, rows)
}
