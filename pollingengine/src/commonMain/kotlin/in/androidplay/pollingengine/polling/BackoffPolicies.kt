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
        maxAttempts = 20, // also bounded by overallTimeoutMs
        overallTimeoutMs = 20_000,
        perAttemptTimeoutMs = null,
    )
}
