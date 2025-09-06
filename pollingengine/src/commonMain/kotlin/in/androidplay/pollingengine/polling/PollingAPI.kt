package `in`.androidplay.pollingengine.polling

/**
 * Public abstraction layer for consumers. Exposes a small, stable API surface.
 * Internals are delegated to the PollingEngine implementation.
 */
public interface PollingApi {
    /** Number of active polling sessions. */
    public fun activePollsCount(): Int

    /** IDs of active polling sessions. */
    public suspend fun listActiveIds(): List<String>

    /** Cancel a session by ID. */
    public suspend fun cancel(id: String)

    /** Cancel a session using the session handle. */
    public suspend fun cancel(session: PollingSession)

    /** Cancel all active sessions. */
    public suspend fun cancelAll()

    /** Shutdown the SDK's internal engine. After shutdown, new sessions cannot be started. */
    public suspend fun shutdown()

    /** Pause a session by ID. */
    public suspend fun pause(id: String)

    /** Resume a session by ID. */
    public suspend fun resume(id: String)

    /** Update backoff/options for a running session by ID. */
    public suspend fun updateBackoff(id: String, newPolicy: BackoffPolicy)

    /** Start a new polling session. Returns a lightweight [PollingSession] handle. */
    public fun <T> startPolling(
        config: PollingConfig<T>,
        onComplete: (PollingOutcome<T>) -> Unit,
    ): PollingSession

    /** One-shot polling that runs to completion synchronously (suspending). */
    public suspend fun <T> run(config: PollingConfig<T>): PollingOutcome<T>

    /** Compose multiple polling configs sequentially. */
    public suspend fun <T> compose(vararg configs: PollingConfig<T>): PollingOutcome<T>
}

