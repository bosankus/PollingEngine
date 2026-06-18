package `in`.androidplay.pollingengine.polling.dsl

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.BackoffPolicies
import `in`.androidplay.pollingengine.polling.BackoffPolicy
import `in`.androidplay.pollingengine.polling.PollingConfig
import `in`.androidplay.pollingengine.polling.PollingEngine
import `in`.androidplay.pollingengine.polling.PollingOutcome
import `in`.androidplay.pollingengine.polling.SharedPoll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Fluent description of a poll, read like a sentence and finished with a verb that picks how it
 * runs. Start from [`Polling.poll`][in.androidplay.pollingengine.polling.Polling.poll]:
 *
 * ```
 * // "Poll the status until it's COMPLETED, every 2 seconds."
 * Polling.poll { api.checkStatus() }
 *     .until { it == "COMPLETED" }
 *     .every(2.seconds)
 *     .start(scope)
 * ```
 *
 * Every refinement is optional with a production-safe default. The terminal verb decides the model:
 * [start]/[await] converge to one outcome, [collect]/[asFlow] stream each value, [shared]
 * multiplexes one loop across subscribers.
 */
@PollingDsl
public class PollBuilder<T> internal constructor(
    private val fetch: suspend () -> PollingResult<T>,
) {
    private var until: ((T) -> Boolean)? = null
    private var stopWhen: (PollingResult<T>) -> Boolean = { false }

    private var intervalMs: Long? = null
    private var explicitBackoff: BackoffPolicy? = null
    private var maxAttempts: Int? = null
    private var overallTimeout: Duration? = null
    private var perAttemptTimeout: Duration? = null

    private var retryWhen: (Error?) -> Boolean = Retry.always
    private var dispatcher: CoroutineDispatcher = Dispatchers.Default
    private var mapErrors: (Throwable) -> Error = { t ->
        Error(-1, t.message ?: (t::class.simpleName ?: "Throwable"))
    }

    private var onAttempt: (attempt: Int, delayMs: Long?) -> Unit = { _, _ -> }
    private var onResult: (attempt: Int, result: PollingResult<T>) -> Unit = { _, _ -> }
    private var onComplete: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit =
        { _, _, _ -> }

    private var keepAliveFor: Duration = Duration.ZERO
    private var replayLast: Int = 1

    // --- terminal condition ---

    /** Stop with success once [predicate] holds for a fetched value. Omit to poll until stopped. */
    public fun until(predicate: (T) -> Boolean): PollBuilder<T> = apply { until = predicate }

    /** Give up (non-success) once [predicate] holds for a fetched value. */
    public fun stopWhen(predicate: (T) -> Boolean): PollBuilder<T> = apply {
        stopWhen = { it is PollingResult.Success && predicate(it.data) }
    }

    /** Advanced: give up based on the raw [PollingResult] (e.g. a specific failure). */
    public fun stopWhenResult(predicate: (PollingResult<T>) -> Boolean): PollBuilder<T> =
        apply { stopWhen = predicate }

    // --- cadence ---

    /** Poll at a constant cadence of [interval] between attempts (no growth, no jitter). */
    public fun every(interval: Duration): PollBuilder<T> =
        apply { intervalMs = interval.inWholeMilliseconds }

    /** Use exponential backoff with jitter; see [BackoffSpec]. Overrides [every]. */
    public fun backoff(spec: BackoffSpec.() -> Unit): PollBuilder<T> =
        apply { explicitBackoff = BackoffSpec().apply(spec).toPolicy() }

    /** Cap the number of attempts. */
    public fun atMost(attempts: Int): PollBuilder<T> = apply { maxAttempts = attempts }

    /** Cap the overall wall-clock time across all attempts. */
    public fun timeout(budget: Duration): PollBuilder<T> = apply { overallTimeout = budget }

    /** Cap each individual attempt; a slower fetch is treated as a (retryable) timeout error. */
    public fun timeoutPerAttempt(budget: Duration): PollBuilder<T> =
        apply { perAttemptTimeout = budget }

    // --- error handling ---

    /** Decide whether to keep polling after a failed attempt. See the [Retry] presets. */
    public fun retryWhen(predicate: (Error?) -> Boolean): PollBuilder<T> =
        apply { retryWhen = predicate }

    /** Map a thrown exception into a domain [Error] for [retryWhen] and reporting. */
    public fun mapErrors(mapper: (Throwable) -> Error): PollBuilder<T> =
        apply { mapErrors = mapper }

    /** Run the poll loop on a specific dispatcher. Defaults to [Dispatchers.Default]. */
    public fun on(dispatcher: CoroutineDispatcher): PollBuilder<T> =
        apply { this.dispatcher = dispatcher }

    // --- observability ---

    /** Called before each attempt with the attempt index and the upcoming delay (0 for immediate). */
    public fun onAttempt(hook: (attempt: Int, delayMs: Long?) -> Unit): PollBuilder<T> =
        apply { onAttempt = hook }

    /** Called after each attempt with its [PollingResult]. */
    public fun onResult(hook: (attempt: Int, result: PollingResult<T>) -> Unit): PollBuilder<T> =
        apply { onResult = hook }

    /** Called once with the terminal outcome, total attempts, and elapsed time. */
    public fun onComplete(
        hook: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit,
    ): PollBuilder<T> = apply { onComplete = hook }

    // --- shared-only knobs ---

    /** [shared] only: keep the loop alive this long after the last subscriber leaves. */
    public fun keepAliveFor(grace: Duration): PollBuilder<T> = apply { keepAliveFor = grace }

    /** [shared] only: replay this many recent values to late subscribers. Default 1. */
    public fun replayLast(count: Int): PollBuilder<T> = apply { replayLast = count }

    // --- terminal verbs ---

    /**
     * Launch into [scope] and return a [PollHandle] for control. Collect [PollHandle.outcomes] for
     * the terminal result; the poll stops when [scope] is cancelled.
     */
    public fun start(scope: CoroutineScope): PollHandle<T> =
        EngineSessionHandle(PollingEngine.launch(scope, buildConfig()))

    /** Suspend until the poll converges and return its single [PollingOutcome]. */
    public suspend fun await(): PollingOutcome<T> = PollingEngine.pollUntil(buildConfig())

    /**
     * Launch into [scope], delivering every successful value to [onValue], and return a
     * [PollHandle] for control. The poll stops when [scope] is cancelled or a stop condition fires.
     */
    public fun collect(scope: CoroutineScope, onValue: suspend (T) -> Unit): PollHandle<T> =
        EngineSessionHandle(PollingEngine.launch(scope, buildConfig(), onValue))

    /** A cold [Flow] that emits every successful value, ideal for `collectAsState` in Compose. */
    public fun asFlow(): Flow<T> = PollingEngine.observe(buildConfig())

    /** Create (or reuse) a multiplexed [SharedPoll] keyed by [key]; one loop fans out to all. */
    public suspend fun shared(key: Any): SharedPoll<T> = PollingEngine.shared(
        key = key,
        config = buildConfig(),
        stopTimeoutMs = keepAliveFor.inWholeMilliseconds,
        replay = replayLast,
    )

    private fun buildBackoff(): BackoffPolicy {
        val base = explicitBackoff
            ?: intervalMs?.let { BackoffPolicies.fixed(intervalMs = it) }
            ?: BackoffPolicy()
        return base.copy(
            maxAttempts = maxAttempts ?: base.maxAttempts,
            overallTimeoutMs = overallTimeout?.inWholeMilliseconds ?: base.overallTimeoutMs,
            perAttemptTimeoutMs = perAttemptTimeout?.inWholeMilliseconds ?: base.perAttemptTimeoutMs,
        )
    }

    internal fun buildConfig(): PollingConfig<T> = PollingConfig(
        fetch = fetch,
        isTerminalSuccess = until ?: { false },
        shouldRetryOnError = retryWhen,
        backoff = buildBackoff(),
        dispatcher = dispatcher,
        onAttempt = onAttempt,
        onResult = onResult,
        onComplete = onComplete,
        throwableMapper = mapErrors,
        stopWhen = stopWhen,
    )
}
