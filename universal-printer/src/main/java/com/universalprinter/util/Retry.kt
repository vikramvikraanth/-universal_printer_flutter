package com.universalprinter.util

import com.universalprinter.model.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs [block], retrying while [retryable] returns true for the thrown error, up to
 * [RetryPolicy.maxAttempts] total attempts with capped exponential backoff. Rethrows the last
 * failure once attempts are exhausted or a non-retryable error is hit. Coroutine cancellation is
 * always propagated, never retried.
 *
 * Pure and dispatcher-agnostic (uses [delay]), so it is unit-testable under virtual time.
 */
suspend fun <T> retrying(
    policy: RetryPolicy,
    retryable: (Throwable) -> Boolean,
    onRetry: (attempt: Int, error: Throwable, delayMs: Long) -> Unit = { _, _, _ -> },
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 1
    var delayMs = policy.initialDelayMs
    while (true) {
        try {
            return block(attempt)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            if (attempt >= policy.maxAttempts || !retryable(error)) throw error
            onRetry(attempt, error, delayMs)
            delay(delayMs)
            delayMs = (delayMs * policy.backoffFactor).toLong().coerceAtMost(policy.maxDelayMs)
            attempt++
        }
    }
}
