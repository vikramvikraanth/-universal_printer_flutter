package com.universalprinter.util

import com.universalprinter.model.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {

    private class Boom(val transient: Boolean) : RuntimeException("boom")

    private val onlyTransient: (Throwable) -> Boolean = { it is Boom && it.transient }

    @Test
    fun succeedsWithoutRetryWhenBlockSucceeds() = runTest {
        var calls = 0
        val result = retrying(RetryPolicy(), onlyTransient) { calls++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun retriesTransientFailuresThenSucceeds() = runTest {
        var calls = 0
        val result = retrying(RetryPolicy(maxAttempts = 4), onlyTransient) {
            calls++
            if (calls < 3) throw Boom(transient = true) else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun exhaustsAttemptsThenRethrowsLastError() = runTest {
        var calls = 0
        val thrown = runCatching {
            retrying(RetryPolicy(maxAttempts = 3), onlyTransient) { calls++; throw Boom(transient = true) }
        }.exceptionOrNull()
        assertTrue(thrown is Boom)
        assertEquals(3, calls)
    }

    @Test
    fun doesNotRetryNonRetryableError() = runTest {
        var calls = 0
        val thrown = runCatching {
            retrying(RetryPolicy(maxAttempts = 5), onlyTransient) { calls++; throw Boom(transient = false) }
        }.exceptionOrNull()
        assertTrue(thrown is Boom)
        assertEquals(1, calls)
    }

    @Test
    fun maxAttemptsOfOneDisablesRetry() = runTest {
        var calls = 0
        runCatching { retrying(RetryPolicy(maxAttempts = 1), onlyTransient) { calls++; throw Boom(transient = true) } }
        assertEquals(1, calls)
    }

    @Test
    fun appliesCappedExponentialBackoffBetweenAttempts() = runTest {
        val policy = RetryPolicy(maxAttempts = 4, initialDelayMs = 100, maxDelayMs = 250, backoffFactor = 2.0)
        val start = testScheduler.currentTime
        runCatching {
            retrying(policy, onlyTransient) { throw Boom(transient = true) }
        }
        // Delays before attempts 2,3,4: 100, 200, min(400,250)=250 → 550ms total; no delay after the last.
        assertEquals(550, testScheduler.currentTime - start)
    }

    @Test
    fun cancellationIsPropagatedNotRetried() = runTest {
        var calls = 0
        try {
            retrying(RetryPolicy(maxAttempts = 5), retryable = { true }) {
                calls++
                throw CancellationException("cancelled")
            }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
        assertEquals(1, calls)
    }
}
