package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.polling.builder.PollingConfigBuilder
import kotlinx.coroutines.flow.Flow

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
        config: PollingConfig<T>
    ): Flow<PollingOutcome<T>>

    /** Start a new polling session. Returns a lightweight [PollingSession] handle. */
    public fun <T> startPolling(
        builder: PollingConfigBuilder<T>.() -> Unit
    ): Flow<PollingOutcome<T>>

    /**
     * Continuous streaming poll: emits the value of **every** successful tick (not just a terminal
     * outcome) and does not auto-complete until a stop condition fires (terminal success,
     * [PollingConfig.stopWhen], a non-retryable failure, or attempt/overall limits). Retryable
     * per-tick errors are surfaced via the builder hooks and skipped. Pair with an unbounded policy
     * (e.g. [BackoffPolicies.fixed]) for an always-on observer.
     */
    public fun <T> observe(
        builder: PollingConfigBuilder<T>.() -> Unit
    ): Flow<T>

    /**
     * Create (or reuse) a multiplexed [SharedPollingSession] for [key]. One poll loop per key makes a
     * single fetch per tick and fans results out to all subscribers, started while subscribed. The
     * same [key] returns the same live session; a different [key] starts a separate one.
     */
    public suspend fun <T> shared(
        key: Any,
        builder: PollingConfigBuilder<T>.() -> Unit
    ): SharedPollingSession<T>

    /** One-shot polling that runs to completion synchronously (suspending). */
    public suspend fun <T> run(config: PollingConfig<T>): PollingOutcome<T>

    /** Compose multiple polling configs sequentially. */
    public suspend fun <T> compose(vararg configs: PollingConfig<T>): PollingOutcome<T>
}
