package `in`.androidplay.pollingengine.polling

/**
 * Factory Pattern: predefined BackoffPolicy configurations for common scenarios.
 */
public object BackoffPolicies {
    /**
     * Quick polling tuned for short-lived availability (e.g., compliance status) with ~20s cap.
     */
    public val quick20s: BackoffPolicy = BackoffPolicy(
        initialDelayMs = 500,
        maxDelayMs = 5_000,
        multiplier = 1.8,
        jitterRatio = 0.2,
        maxAttempts = 20,
        overallTimeoutMs = 20_000,
        perAttemptTimeoutMs = 10000,
    )

    /**
     * Constant-cadence polling: one attempt every [intervalMs] with no growth and no jitter.
     *
     * By default the cadence is unbounded ([BackoffPolicy.UNLIMITED_ATTEMPTS] attempts and
     * [BackoffPolicy.NO_TIMEOUT]) which is the natural fit for a long-lived observer — pair it
     * with [Polling.observe] or [Polling.shared]. Supply [maxAttempts]/[overallTimeoutMs] to bound it.
     *
     * Example:
     * ```
     * val tick = BackoffPolicies.fixed(intervalMs = 10_000) // poll every 10s, forever
     * ```
     *
     * @param intervalMs spacing between attempts in milliseconds; must be > 0.
     * @param perAttemptTimeoutMs optional per-attempt timeout; null to disable.
     * @param maxAttempts attempt cap, or [BackoffPolicy.UNLIMITED_ATTEMPTS] (default) for none.
     * @param overallTimeoutMs overall budget, or [BackoffPolicy.NO_TIMEOUT] (default) for none.
     */
    public fun fixed(
        intervalMs: Long,
        perAttemptTimeoutMs: Long? = null,
        maxAttempts: Int = BackoffPolicy.UNLIMITED_ATTEMPTS,
        overallTimeoutMs: Long = BackoffPolicy.NO_TIMEOUT,
    ): BackoffPolicy {
        require(intervalMs > 0) { "intervalMs must be > 0, was $intervalMs" }
        return BackoffPolicy(
            initialDelayMs = intervalMs,
            maxDelayMs = intervalMs,
            multiplier = 1.0,
            jitterRatio = 0.0,
            maxAttempts = maxAttempts,
            overallTimeoutMs = overallTimeoutMs,
            perAttemptTimeoutMs = perAttemptTimeoutMs,
        )
    }
}
