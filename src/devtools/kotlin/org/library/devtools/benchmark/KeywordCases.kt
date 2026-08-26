package org.library.devtools.benchmark

data class KeywordCase(val label: String, val keyword: String, val note: String)

// ExplainAnalyzer(3-way 속도·정확도 비교)와 SuggestAnalyzer(오타 보정)가 완전히 같은 케이스를 쓰도록
// 한곳에 모아둔다. 두 도구의 결과 표를 나란히 비교하려면 케이스가 어긋나면 안 된다.
object KeywordCases {

    val all = listOf(
        KeywordCase("흔한 키워드", "스프링", "다수 매칭 예상 (tier S)"),
        KeywordCase("희귀 키워드", "미니멀리즘 실천법", "소수 건만 매칭 예상 (tier Rare)"),
        KeywordCase("접두 검색", "클린", "'클린 코드'/'클린 아키텍처' 등 접두 매칭"),
        KeywordCase("형태소 경계를 넘는 부분 문자열", "린코", "'클린 코드'의 어중간한 substring, 단어 경계 무시"),
        KeywordCase("오타(fuzzy)", "스프릥", "'스프링'의 1글자 오타"),
    )
}
