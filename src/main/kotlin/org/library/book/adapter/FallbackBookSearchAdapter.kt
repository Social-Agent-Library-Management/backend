package org.library.book.adapter

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.library.book.application.port.BookSearchPort
import org.library.book.application.port.BookSearchResult
import org.library.core.presentation.PageRequestParams
import org.opensearch.client.ResponseException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

/**
 * OpenSearch 검색이 연결 실패·타임아웃·5xx로 실패하면 RDB(LIKE)로 대체 조회한다.
 * 최근 요청의 실패율이 기준을 넘으면 서킷을 열어 OpenSearch를 부르지 않고 바로 RDB로 응답한다.
 * 4xx는 쿼리 오류이므로 실패로 세지 않고 대체하지도 않는다.
 */
@Primary
@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "opensearch")
class FallbackBookSearchAdapter(
    private val openSearchAdapter: OpenSearchBookSearchAdapter,
    private val rdbAdapter: MysqlLikeBookSearchAdapter,
    @Value("\${search.fallback.sliding-window-size:10}") slidingWindowSize: Int,
    @Value("\${search.fallback.minimum-number-of-calls:5}") minimumNumberOfCalls: Int,
    @Value("\${search.fallback.failure-rate-threshold:50}") failureRateThreshold: Float,
    @Value("\${search.fallback.open-duration:PT30S}") openDuration: Duration,
) : BookSearchPort {

    val circuitBreaker: CircuitBreaker = CircuitBreaker.of(
        CIRCUIT_NAME,
        circuitBreakerConfig(slidingWindowSize, minimumNumberOfCalls, failureRateThreshold, openDuration),
    ).apply {
        eventPublisher.onStateTransition { log.warn { "OpenSearch 검색 서킷 상태 변경: ${it.stateTransition}" } }
    }

    override fun search(query: String?, page: PageRequestParams): BookSearchResult {
        if (!circuitBreaker.tryAcquirePermission()) return searchRdb(query, page)

        val start = System.nanoTime()
        return try {
            openSearchAdapter.search(query, page)
                .also { circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS) }
        } catch (e: Exception) {
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e)
            if (!isUnavailable(e)) throw e
            log.warn(e) { "OpenSearch 검색 실패, RDB로 대체 조회합니다. circuit=${circuitBreaker.state}" }
            searchRdb(query, page)
        }
    }

    private fun searchRdb(query: String?, page: PageRequestParams): BookSearchResult =
        rdbAdapter.search(query, page).copy(degraded = true)

    companion object {
        const val CIRCUIT_NAME = "opensearch-search"
        private const val TOO_MANY_REQUESTS = 429

        fun circuitBreakerConfig(
            slidingWindowSize: Int,
            minimumNumberOfCalls: Int,
            failureRateThreshold: Float,
            openDuration: Duration,
        ): CircuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(slidingWindowSize)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .failureRateThreshold(failureRateThreshold)
            .waitDurationInOpenState(openDuration)
            .permittedNumberOfCallsInHalfOpenState(1)
            .recordException(::isUnavailable)
            .build()

        /** 검색 엔진을 쓸 수 없는 오류(연결 실패·타임아웃·5xx·429)만 실패로 센다. */
        fun isUnavailable(e: Throwable): Boolean = when (e) {
            is ResponseException -> e.response.statusLine.statusCode.let { it >= 500 || it == TOO_MANY_REQUESTS }
            is IOException -> true
            else -> false
        }
    }
}
