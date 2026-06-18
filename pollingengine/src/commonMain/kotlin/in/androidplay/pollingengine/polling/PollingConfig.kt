package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Configuration for the polling engine.
 */
public data class PollingConfig<T>(
    val fetch: suspend () -> PollingResult<T>,
    val isTerminalSuccess: (T) -> Boolean,
    val shouldRetryOnError: (Error?) -> Boolean = { true },
    val backoff: BackoffPolicy = BackoffPolicy(),
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val onAttempt: (attempt: Int, delayMs: Long?) -> Unit = { _, _ -> },
    val onResult: (attempt: Int, result: PollingResult<T>) -> Unit = { _, _ -> },
    val onComplete: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit = { _, _, _ -> },
    /** Maps any thrown exception into a domain [Error] used by retry predicates and reporting. */
    val throwableMapper: (Throwable) -> Error = { t ->
        val msg = t.message ?: (t::class.simpleName ?: "Throwable")
        // Use a stable public default code without exposing internal ErrorCodes
        Error(-1, msg)
    },
    /**
     * Optional non-success terminal predicate. When it returns true for a poll result,
     * polling ends immediately *without* a [PollingOutcome.Success] — the converge APIs
     * ([Polling.run]/[Polling.startPolling]) report [PollingOutcome.Exhausted] carrying that
     * result, and the streaming APIs ([Polling.observe]/[Polling.shared]) simply complete.
     * Distinct from [isTerminalSuccess]; defaults to never stopping.
     *
     * Example: stop a list observer once the remote list drains.
     * ```
     * stopWhen = { it is PollingResult.Success && it.data.isEmpty() }
     * ```
     */
    val stopWhen: (PollingResult<T>) -> Boolean = { false },
)
