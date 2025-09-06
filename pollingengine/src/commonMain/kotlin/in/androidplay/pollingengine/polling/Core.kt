package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult


// --- Internal error codes to decouple from external sources ---
internal object ErrorCodes {
    internal const val UNKNOWN_ERROR_CODE: Int = -1
    internal const val NETWORK_ERROR: Int = 1001
    internal const val SERVER_ERROR_CODE: Int = 500
    internal const val TIMEOUT_ERROR_CODE: Int = 1002
}

// --- Strategies ---
internal interface FetchStrategy<T> {
    suspend fun fetch(): PollingResult<T>
}

internal interface SuccessStrategy<T> {
    fun isTerminal(value: T): Boolean
}

internal interface RetryStrategy {
    fun shouldRetry(error: Error?): Boolean
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

/**
 * Common retry strategies for polling. Use these to avoid boilerplate and ensure consistency.
 */
public object RetryPredicates {
    /**
     * Retry for network-related, server, timeout, and unknown errors.
     * Recommended for most network polling scenarios.
     */
    public val networkOrServerOrTimeout: (Error?) -> Boolean = { err ->
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
