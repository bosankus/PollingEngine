package `in`.androidplay.pollingengine.polling

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Configuration for exponential backoff with jitter.
 * Provides validation to ensure safe, production-ready defaults.
 */
public data class BackoffPolicy(
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 30_000,
    val multiplier: Double = 2.0,
    /** Ratio in [0.0, 1.0]. 0 = no jitter. */
    val jitterRatio: Double = 0.2,
    val maxAttempts: Int = 8,
    val overallTimeoutMs: Long = 120_000,
    /** Optional per-attempt timeout; null to disable. Must be > 0 when set. */
    val perAttemptTimeoutMs: Long? = null,
    /** Random source for jitter; injectable for deterministic tests. */
    val random: Random = Random.Default,
) {
    init {
        require(initialDelayMs >= 0) { "initialDelayMs must be >= 0, was $initialDelayMs" }
        require(maxDelayMs > 0) { "maxDelayMs must be > 0, was $maxDelayMs" }
        require(maxAttempts > 0) { "maxAttempts must be > 0, was $maxAttempts" }
        require(overallTimeoutMs > 0) { "overallTimeoutMs must be > 0, was $overallTimeoutMs" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0, was $multiplier" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be in [0.0, 1.0], was $jitterRatio" }
        if (perAttemptTimeoutMs != null) {
            require(perAttemptTimeoutMs > 0) { "perAttemptTimeoutMs must be > 0 when set, was $perAttemptTimeoutMs" }
        }
        require(maxDelayMs >= initialDelayMs) {
            "maxDelayMs ($maxDelayMs) must be >= initialDelayMs ($initialDelayMs)"
        }
    }

    /**
     * Computes randomized delay around a base using jitter, clamped to [0, maxDelayMs].
     */
    public fun computeJitteredDelay(baseMs: Long): Long {
        if (baseMs <= 0L || jitterRatio == 0.0) return baseMs.coerceIn(0L, maxDelayMs)
        val safeBase = baseMs.coerceAtMost(maxDelayMs)
        val jitter = (safeBase * jitterRatio).toLong()
        val minBound = max(0L, safeBase - jitter)
        val maxBound = min(maxDelayMs, safeBase + jitter)
        if (maxBound <= minBound) return minBound
        val exclusiveUpper = if (maxBound == Long.MAX_VALUE) maxBound else maxBound + 1
        return random.nextLong(minBound, exclusiveUpper)
    }
}
