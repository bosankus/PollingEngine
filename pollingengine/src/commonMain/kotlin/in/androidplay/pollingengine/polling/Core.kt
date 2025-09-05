package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult

// --- Observability contracts ---
public interface Logger {
    public fun log(level: String, message: String, throwable: Throwable? = null): Unit
}

public interface Metrics {
    public fun recordAttempt(attempt: Int, delayMs: Long?): Unit {}
    public fun recordResult(attempt: Int, result: PollingResult<*>): Unit {}
    public fun recordComplete(attempts: Int, durationMs: Long): Unit {}
}

// --- Internal error codes to decouple from external sources ---
public object ErrorCodes {
    public const val UNKNOWN_ERROR_CODE: Int = -1
    public const val NETWORK_ERROR: Int = 1001
    public const val SERVER_ERROR_CODE: Int = 500
    public const val TIMEOUT_ERROR_CODE: Int = 1002
}

// --- Strategies ---
public interface FetchStrategy<T> {
    public suspend fun fetch(): PollingResult<T>
}

public interface SuccessStrategy<T> {
    public fun isTerminal(value: T): Boolean
}

public interface RetryStrategy {
    public fun shouldRetry(error: Error?): Boolean
}

internal class LambdaFetchStrategy<T>(private val block: suspend () -> PollingResult<T>) : FetchStrategy<T> {
    override suspend fun fetch(): PollingResult<T> = block()
}

internal class LambdaSuccessStrategy<T>(private val predicate: (T) -> Boolean) : SuccessStrategy<T> {
    override fun isTerminal(value: T): Boolean = predicate(value)
}

internal class LambdaRetryStrategy(private val predicate: (Error?) -> Boolean) : RetryStrategy {
    override fun shouldRetry(error: Error?): Boolean = predicate(error)
}

// --- Default retry predicates ---
public object DefaultRetryPredicates {
    /**
     * Retry for network-related, server and timeout errors; also retries unknowns by default.
     */
    public val retryOnNetworkServerTimeout: (Error?) -> Boolean = { err ->
        when (err?.code) {
            ErrorCodes.NETWORK_ERROR, ErrorCodes.SERVER_ERROR_CODE, ErrorCodes.TIMEOUT_ERROR_CODE, ErrorCodes.UNKNOWN_ERROR_CODE -> true
            else -> false
        }
    }

    /** Always retry regardless of error. */
    public val always: (Error?) -> Boolean = { true }

    /** Never retry on error. */
    public val never: (Error?) -> Boolean = { false }
}

// --- Polling outcome ---
public sealed class PollingOutcome<out T> {
    public data class Success<T>(val value: T, val attempts: Int, val elapsedMs: Long) : PollingOutcome<T>()
    public data class Exhausted(val last: PollingResult<*>?, val attempts: Int, val elapsedMs: Long) : PollingOutcome<Nothing>()
    public data class Timeout(val last: PollingResult<*>?, val attempts: Int, val elapsedMs: Long) : PollingOutcome<Nothing>()
    public data class Cancelled(val attempts: Int, val elapsedMs: Long) : PollingOutcome<Nothing>()
}
