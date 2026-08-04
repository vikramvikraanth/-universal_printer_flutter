package com.universalprinter.model

/**
 * Retry policy for transient (connectivity) failures. [maxAttempts] is the total number of tries
 * including the first, so `maxAttempts = 1` disables retrying. Backoff grows geometrically from
 * [initialDelayMs] by [backoffFactor], capped at [maxDelayMs].
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 300,
    val maxDelayMs: Long = 3_000,
    val backoffFactor: Double = 2.0,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(initialDelayMs >= 0) { "initialDelayMs must be >= 0" }
        require(backoffFactor >= 1.0) { "backoffFactor must be >= 1.0" }
    }

    companion object {
        /** Do not retry — a single attempt. */
        val NONE = RetryPolicy(maxAttempts = 1)

        /** 3 attempts, 300ms → 600ms backoff. */
        val DEFAULT = RetryPolicy()
    }
}
