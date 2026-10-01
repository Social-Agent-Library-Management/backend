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
import java.util.concurrent.TimeoutException

private val log = KotlinLogging.logger {}

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
        if (!circuitBreaker.tryAcquirePermission()) return rdbAdapter.search(query, page)

        val start = System.nanoTime()
        return try {
            openSearchAdapter.search(query, page)
                .also { circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS) }
        } catch (e: Exception) {
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e)
            if (!isUnavailable(e)) throw e
            log.warn(e) { "OpenSearch 검색 실패, RDB로 대체 조회합니다. circuit=${circuitBreaker.state}" }
            rdbAdapter.search(query, page)
        } catch (e: Error) {
            circuitBreaker.releasePermission()
            throw e
        }
    }

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
            .maxWaitDurationInHalfOpenState(openDuration)
            .recordException(::isUnavailable)
            .build()

        fun isUnavailable(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
            when (it) {
                is ResponseException -> it.response.statusLine.statusCode.let { c -> c >= 500 || c == TOO_MANY_REQUESTS }
                is IOException, is TimeoutException -> true
                else -> false
            }
        }
    }
}
