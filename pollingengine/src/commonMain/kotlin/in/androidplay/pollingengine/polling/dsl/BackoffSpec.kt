package `in`.androidplay.pollingengine.polling.dsl

import `in`.androidplay.pollingengine.polling.BackoffPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Describes exponential backoff in plain terms for [PollBuilder.backoff] and
 * [PollHandle.retune]. Every knob has a production-safe default, so set only what you care about:
 *
 * ```
 * .backoff {
 *     initialDelay = 500.milliseconds
 *     maxDelay = 5.seconds
 *     multiplier = 1.8
 *     jitter = 0.15
 * }
 * ```
 *
 * Attempt/time limits set here can be overridden by the more readable [PollBuilder.atMost],
 * [PollBuilder.timeout], and [PollBuilder.timeoutPerAttempt].
 */
@PollingDsl
public class BackoffSpec {
    /** Delay before the second attempt; grows by [multiplier] each round. Default 500ms. */
    public var initialDelay: Duration = 500.milliseconds

    /** Ceiling the growing delay is clamped to. Default 30s. */
    public var maxDelay: Duration = 30.seconds

    /** Growth factor applied to the delay each round (>= 1.0). Default 2.0. */
    public var multiplier: Double = 2.0

    /** Randomization ratio in [0.0, 1.0] applied to each delay to avoid thundering herds. */
    public var jitter: Double = 0.2

    /** Maximum number of attempts, or `null` for no attempt limit. Default 8. */
    public var maxAttempts: Int? = 8

    /** Overall wall-clock budget, or `null` for no overall timeout. Default 120s. */
    public var overallTimeout: Duration? = 120.seconds

    /** Optional per-attempt timeout; `null` disables it. */
    public var perAttemptTimeout: Duration? = null

    internal fun toPolicy(): BackoffPolicy =
        BackoffPolicy(
            initialDelayMs = initialDelay.inWholeMilliseconds,
            maxDelayMs = maxDelay.inWholeMilliseconds,
            multiplier = multiplier,
            jitterRatio = jitter,
            maxAttempts = maxAttempts ?: BackoffPolicy.UNLIMITED_ATTEMPTS,
            overallTimeoutMs = overallTimeout?.inWholeMilliseconds ?: BackoffPolicy.NO_TIMEOUT,
            perAttemptTimeoutMs = perAttemptTimeout?.inWholeMilliseconds,
        )
}
