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

/**
 * Common retry predicates, reused by the public [Retry] presets and the engine defaults.
 */
internal object RetryPredicates {
    /**
     * Retry for network-related, server, timeout, and unknown errors.
     * Recommended for most network polling scenarios.
     */
    val networkOrServerOrTimeout: (Error?) -> Boolean = { err ->
        when (err?.code) {
            ErrorCodes.NETWORK_ERROR,
            ErrorCodes.SERVER_ERROR_CODE,
            ErrorCodes.TIMEOUT_ERROR_CODE,
            ErrorCodes.UNKNOWN_ERROR_CODE,
            -> true
            else -> false
        }
    }

    /** Always retry regardless of error. */
    val always: (Error?) -> Boolean = { true }

    /** Never retry on error. */
    val never: (Error?) -> Boolean = { false }
}

// --- Polling outcome ---
public sealed class PollingOutcome<out T> {
    public data class Success<T>(
        val value: T,
        val attempts: Int,
        val elapsedMs: Long,
    ) : PollingOutcome<T>()

    public data class Exhausted(
        val last: PollingResult<*>?,
        val attempts: Int,
        val elapsedMs: Long,
    ) : PollingOutcome<Nothing>()

    public data class Timeout(
        val last: PollingResult<*>?,
        val attempts: Int,
        val elapsedMs: Long,
    ) : PollingOutcome<Nothing>()

    public data class Cancelled(
        val attempts: Int,
        val elapsedMs: Long,
    ) : PollingOutcome<Nothing>()
}
