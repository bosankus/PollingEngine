package `in`.androidplay.pollingengine.polling.builder

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.BackoffPolicy
import `in`.androidplay.pollingengine.polling.PollingConfig
import `in`.androidplay.pollingengine.polling.PollingOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@DslMarker
public annotation class PollingBuilderMarker

@PollingBuilderMarker
public class PollingConfigBuilder<T> {

    public var fetch: (suspend () -> PollingResult<T>)? = null
    public var isTerminalSuccess: ((T) -> Boolean)? = null
    public var shouldRetryOnError: (Error?) -> Boolean = { true }
    public var backoff: BackoffPolicy = BackoffPolicy()
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
    public var onAttempt: (attempt: Int, delayMs: Long?) -> Unit = { _, _ -> }
    public var onResult: (attempt: Int, result: PollingResult<T>) -> Unit = { _, _ -> }
    public var onComplete: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit =
        { _, _, _ -> }
    public var throwableMapper: (Throwable) -> Error = { t ->
        val msg = t.message ?: (t::class.simpleName ?: "Throwable")
        Error(-1, msg)
    }

    /**
     * Optional non-success terminal predicate. See [PollingConfig.stopWhen]. Defaults to never.
     */
    public var stopWhen: (PollingResult<T>) -> Boolean = { false }

    /**
     * Streaming-only: grace period (ms) a [in.androidplay.pollingengine.polling.shared] session
     * keeps polling after its last subscriber leaves before stopping. 0 stops immediately.
     * Ignored by [build]/`startPolling`/`run`.
     */
    public var stopTimeoutMs: Long = 0

    /**
     * Streaming-only: number of most-recent values replayed to late subscribers of a
     * [in.androidplay.pollingengine.polling.shared] session. Defaults to 1 (last value).
     * Ignored by [build]/`startPolling`/`run`.
     */
    public var replay: Int = 1

    /**
     * Builds a converge-mode [PollingConfig]. Requires both [fetch] and [isTerminalSuccess].
     * Used by `startPolling`/`run`/`compose`.
     */
    public fun build(): PollingConfig<T> = build(
        terminalSuccess = isTerminalSuccess
            ?: throw IllegalStateException("isTerminalSuccess must be set"),
    )

    /**
     * Builds a streaming-mode [PollingConfig] for `observe`/`shared`. [isTerminalSuccess] is
     * optional here (defaults to "never terminal") so the stream can run indefinitely.
     */
    internal fun buildForStreaming(): PollingConfig<T> = build(
        terminalSuccess = isTerminalSuccess ?: { false },
    )

    private fun build(terminalSuccess: (T) -> Boolean): PollingConfig<T> {
        return PollingConfig(
            fetch = fetch ?: throw IllegalStateException("fetch must be set"),
            isTerminalSuccess = terminalSuccess,
            shouldRetryOnError = shouldRetryOnError,
            backoff = backoff,
            dispatcher = dispatcher,
            onAttempt = onAttempt,
            onResult = onResult,
            onComplete = onComplete,
            throwableMapper = throwableMapper,
            stopWhen = stopWhen,
        )
    }
}
