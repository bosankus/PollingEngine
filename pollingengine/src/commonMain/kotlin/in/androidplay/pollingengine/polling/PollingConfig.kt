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
        Error(ErrorCodes.UNKNOWN_ERROR_CODE, msg)
    },
    val logger: Logger? = null,
    val metrics: Metrics? = null,
) {
    init {
        requireNotNull(fetch) { "fetch must not be null" }
        requireNotNull(isTerminalSuccess) { "isTerminalSuccess must not be null" }
        requireNotNull(shouldRetryOnError) { "shouldRetryOnError must not be null" }
        requireNotNull(dispatcher) { "dispatcher must not be null" }
    }
}
