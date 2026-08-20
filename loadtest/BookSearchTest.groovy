// ngrinder 부하테스트 스크립트: GET /books/search
//
// 사용법 (같은 스크립트로 세 번 측정해서 비교):
//   1) 앱을 search.engine=mysql(기본값)로 띄우고 BASE_URL을 그 주소로 맞춘 뒤 ngrinder에서 실행 -> LIKE 결과
//   2) 앱을 search.engine=fulltext로 띄우고(devtools `MysqlDataGenerator`로 FULLTEXT 인덱스가 이미 설치돼 있어야 함)
//      같은 스크립트를 그대로 실행 -> FULLTEXT(ngram) 결과
//   3) 앱을 search.engine=opensearch로 띄우고(devtools `OpenSearchIndexer`로 books 인덱스가 이미
//      색인돼 있어야 함) 같은 스크립트를 그대로 실행 -> OpenSearch(nori + fuzziness) 결과
//   세 실행의 ngrinder 리포트(TPS, Mean Test Time, 90th percentile)를 나란히 비교한다.
//
// 키워드 세트는 devtools ExplainAnalyzer.kt / MysqlDataGenerator.kt의 케이스와 동일하게 맞춰서,
// 실행계획 분석 -> 실측 응답시간까지 같은 시나리오로 이어지도록 했다.

import static net.grinder.script.Grinder.grinder
import static org.junit.Assert.assertThat
import static org.hamcrest.Matchers.is

import net.grinder.plugin.http.HTTPPluginControl
import net.grinder.plugin.http.HTTPRequest
import net.grinder.script.GTest
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import HTTPClient.HTTPResponse
import HTTPClient.NVPair
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GrinderRunner)
class BookSearchTest {

    // 검증용 인스턴스에 맞춰 바꿔서 사용 (MySQL LIKE 측정 시 8080, FULLTEXT/OpenSearch 측정 시 8081 등).
    public static final String BASE_URL = "http://localhost:8080"

    // ExplainAnalyzer.kt / MysqlDataGenerator.kt와 동일한 케이스: 흔한/희귀/접두/부분문자열/오타.
    public static final List<String> KEYWORDS = [
        "스프링",             // 흔한 키워드 (tier S, 다수 매칭)
        "미니멀리즘 실천법",   // 희귀 키워드 (정확한 매칭 건수 있음)
        "클린",               // 접두 검색
        "린코",               // 형태소 경계를 넘는 부분 문자열
        "스프릥",             // 오타
    ]

    public static GTest test
    public static HTTPRequest request

    @BeforeProcess
    static void beforeProcess() {
        HTTPPluginControl.getConnectionDefaults().timeout = 6000
        test = new GTest(1, "GET /books/search")
        request = new HTTPRequest()
        grinder.logger.info("BASE_URL = ${BASE_URL}")
    }

    @Before
    void before() {
        test.record(this, "search")
    }

    @Test
    void search() {
        def keyword = KEYWORDS[grinder.threadNumber % KEYWORDS.size()]
        def encoded = URLEncoder.encode(keyword, "UTF-8")
        HTTPResponse result = request.GET(
            "${BASE_URL}/books/search?q=${encoded}&page=1&pageSize=20",
            [] as NVPair[],
        )
        assertThat(result.statusCode, is(200))
    }
}
