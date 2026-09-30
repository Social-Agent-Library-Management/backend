package org.library.book.adapter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State
import org.library.book.application.SearchBooksService
import org.library.book.domain.entity.Book
import org.library.book.domain.repository.BookRepository
import org.library.core.presentation.PageRequestParams
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OpenSearch 앞에 TCP 프록시를 두고 연결 끊김·무응답을 주입해 RDB 대체 조회와 서킷 브레이커를 검증한다.
 *   docker compose up -d   후 ./gradlew test --tests '*BookSearchFallbackTest*'
 */
@SpringBootTest(
    properties = [
        "search.engine=opensearch",
        "search.index.outbox.poll-interval=PT1H",
        "search.fallback.sliding-window-size=10",
        "search.fallback.minimum-number-of-calls=3",
        "search.fallback.failure-rate-threshold=50",
        "search.fallback.open-duration=PT2S",
    ],
)
class BookSearchFallbackTest {

    @Autowired lateinit var searchBooksService: SearchBooksService
    @Autowired lateinit var fallbackAdapter: FallbackBookSearchAdapter
    @Autowired lateinit var bookRepository: BookRepository

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        assumeTrue(openSearchReachable(), "OpenSearch(localhost:$OPENSEARCH_PORT)가 없어 건너뜁니다.")
        proxy.mode = ToggleProxy.Mode.FORWARD
        bookId = bookRepository.save(Book(title = BOOK_TITLE, author = "저자", isbn = null, publisher = "출판사")).id
    }

    @AfterEach
    fun tearDown() {
        proxy.mode = ToggleProxy.Mode.FORWARD
        bookRepository.deleteById(bookId)
    }

    @Test
    fun `OpenSearch 장애 동안 검색은 RDB로 응답하고 실패율이 기준을 넘으면 타임아웃 없이 바로 대체하며 복구되면 되돌아온다`() {
        val healthy = search()
        assertFalse(healthy.degraded)
        assertEquals(State.CLOSED, fallbackAdapter.circuitBreaker.state)

        // 정상 1건 + 연결 끊김 1건 + 응답 없음 1건 → 최근 3건 중 실패율 67%로 차단
        proxy.mode = ToggleProxy.Mode.DROP
        val dropped = timed { search() }
        proxy.mode = ToggleProxy.Mode.HANG
        val timedOut = listOf(timed { search() })

        (listOf(dropped) + timedOut).forEach { (response, _) ->
            assertTrue(response.degraded)
            assertEquals(listOf(BOOK_TITLE), response.books.map { it.title })
        }
        timedOut.forEach { (_, millis) -> assertTrue(millis in 900..2_500, "무응답은 응답 타임아웃(1초)에 끊겨야 한다. ${millis}ms") }
        assertEquals(State.OPEN, fallbackAdapter.circuitBreaker.state)

        // 차단 중: OpenSearch를 부르지 않고 바로 RDB
        val open = (1..20).map { timed { search() } }
        assertTrue(open.all { it.first.degraded && it.first.books.map { b -> b.title } == listOf(BOOK_TITLE) })
        assertTrue(open.maxOf { it.second } < 200, "차단 중에는 타임아웃을 기다리지 않아야 한다. ${open.map { it.second }}")

        // 복구: 차단 시간이 지나기 전까지는 계속 RDB, 지난 뒤 시험 요청이 성공하면 OpenSearch로 복귀
        proxy.mode = ToggleProxy.Mode.FORWARD
        assertTrue(search().degraded)
        Thread.sleep(2_100)
        val recovered = search()
        assertFalse(recovered.degraded)
        assertEquals(State.CLOSED, fallbackAdapter.circuitBreaker.state)

        println(
            "[search-fallback] 장애 중 검색 ${1 + timedOut.size + open.size}건 모두 RDB 응답(성공 100%), " +
                "연결 끊김=${dropped.second}ms, 무응답=${timedOut.map { it.second }}ms, " +
                "차단 중 ${open.size}건 평균=${open.map { it.second }.average().toInt()}ms 최대=${open.maxOf { it.second }}ms, " +
                "복구 후 degraded=${recovered.degraded}",
        )
    }

    private fun search(): SearchBooksService.Response =
        searchBooksService.execute(BOOK_TITLE, PageRequestParams())

    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val begin = System.nanoTime()
        val result = block()
        return result to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin)
    }

    private fun openSearchReachable(): Boolean = runCatching { Socket("localhost", OPENSEARCH_PORT).close() }.isSuccess

    /** 모드에 따라 OpenSearch로 전달하거나, 연결을 바로 끊거나, 연결만 유지하고 응답하지 않는다. */
    class ToggleProxy(private val targetPort: Int) {

        enum class Mode { FORWARD, DROP, HANG }

        private val server = ServerSocket(0)
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val pool = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }

        val port: Int get() = server.localPort

        /** 모드를 바꾸면 커넥션 풀에 남은 연결도 끊어 다음 요청부터 새 모드가 적용되게 한다. */
        @Volatile var mode = Mode.FORWARD
            set(value) {
                field = value
                sockets.forEach { runCatching { it.close() } }
                sockets.clear()
            }

        init {
            pool.submit {
                while (!server.isClosed) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    when (mode) {
                        Mode.DROP -> client.close()
                        Mode.HANG -> sockets += client
                        Mode.FORWARD -> {
                            val upstream = Socket("localhost", targetPort)
                            sockets += client
                            sockets += upstream
                            pool.submit { pipe(client, upstream) }
                            pool.submit { pipe(upstream, client) }
                        }
                    }
                }
            }
        }

        private fun pipe(from: Socket, to: Socket) {
            runCatching { from.getInputStream().transferTo(to.getOutputStream()) }
            runCatching { from.close() }
            runCatching { to.close() }
        }
    }

    companion object {
        private const val OPENSEARCH_PORT = 9200
        private const val BOOK_TITLE = "대체조회검증도서"

        private val proxy = ToggleProxy(OPENSEARCH_PORT)

        @JvmStatic
        @DynamicPropertySource
        fun openSearchThroughProxy(registry: DynamicPropertyRegistry) {
            registry.add("opensearch.port") { proxy.port }
        }
    }
}
