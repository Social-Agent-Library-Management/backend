package org.library.book.adapter

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 운영 설정값(최근 10건 · 최소 5건 · 실패율 50%)으로 서킷 판단 기준을 확인한다. */
class SearchCircuitBreakerConfigTest {

    private val breaker = CircuitBreaker.of(
        "test",
        FallbackBookSearchAdapter.circuitBreakerConfig(
            slidingWindowSize = 10,
            minimumNumberOfCalls = 5,
            failureRateThreshold = 50f,
            openDuration = Duration.ofSeconds(30),
        ),
    )

    @Test
    fun `성공과 실패가 섞여도 최근 실패율이 기준을 넘으면 차단한다`() {
        // 성공·실패가 번갈아 와서 '연속 실패'는 2번을 넘지 않지만 실패율은 60%
        listOf(true, false, false, true, false, true, false, false, true, false).forEach { ok ->
            if (breaker.state == State.OPEN) return@forEach
            if (ok) success() else failure(SocketTimeoutException("timeout"))
        }

        assertEquals(State.OPEN, breaker.state)
        assertFalse(breaker.tryAcquirePermission())
    }

    @Test
    fun `호출 수가 최소 기준보다 적으면 모두 실패해도 차단하지 않는다`() {
        repeat(4) { failure(IOException("connection refused")) }

        assertEquals(State.CLOSED, breaker.state)
    }

    @Test
    fun `쿼리 오류 같은 다른 예외는 실패로 세지 않는다`() {
        repeat(10) { failure(IllegalStateException("응답 파싱 실패")) }

        assertEquals(State.CLOSED, breaker.state)
        assertEquals(0f, breaker.metrics.failureRate.coerceAtLeast(0f))
        assertTrue(breaker.tryAcquirePermission())
    }

    private fun success() {
        breaker.tryAcquirePermission()
        breaker.onSuccess(1, TimeUnit.MILLISECONDS)
    }

    private fun failure(e: Throwable) {
        breaker.tryAcquirePermission()
        breaker.onError(1, TimeUnit.MILLISECONDS, e)
    }
}
