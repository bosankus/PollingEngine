package `in`.androidplay.pollingengine.polling

import kotlinx.coroutines.flow.Flow

/**
 * A multiplexed, subscriber-driven polling session created by [Polling.shared].
 *
 * One underlying poll loop runs per [key] and makes a single [PollingConfig.fetch] call per tick,
 * fanning each successful value out to every subscriber. Polling is subscriber-driven: it starts on
 * the first subscriber and stops a configurable grace period after the last subscriber leaves
 * (see `stopTimeoutMs`/`replay` on the builder), then restarts automatically on the next subscriber.
 *
 * Example:
 * ```
 * val session = Polling.shared(key = vin) {
 *     fetch = { repository.getServicesList(vin).toPollingResult() }
 *     backoff = BackoffPolicies.fixed(10_000)                          // one tick / 10s, forever
 *     stopWhen = { it is PollingResult.Success && it.data.isEmpty() }  // stop when the list drains
 *     stopTimeoutMs = 15_000                                           // keep alive 15s after last leaves
 * }
 *
 * // Both views are fed by the SAME 10s network call:
 * val activations  = session.stream { services -> services.any { it.isActive } }
 * val associations = session.stream { services -> services.carAssociationId.isNotEmpty() }
 * ```
 */
public interface SharedPollingSession<T> {
    /** The key this session is registered under (e.g. a VIN). */
    public val key: Any

    /** A view that emits every per-tick success value from the shared upstream poll. */
    public fun stream(): Flow<T>

    /**
     * A filtered view over the same shared upstream poll. Applying a [filter] here does **not**
     * trigger an additional network call — all views share one underlying fetch per tick.
     */
    public fun stream(filter: (T) -> Boolean): Flow<T>
}
