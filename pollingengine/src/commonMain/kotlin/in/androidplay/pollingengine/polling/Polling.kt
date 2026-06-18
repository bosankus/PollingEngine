package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.dsl.PollBuilder

/**
 * The single entry point to the polling engine. Start a poll by describing it in a sentence:
 *
 * ```
 * // Converge: poll until done, then stop.
 * Polling.poll { api.checkStatus() }
 *     .until { it == "COMPLETED" }
 *     .every(2.seconds)
 *     .start(scope)
 *
 * // Observe: react to every value.
 * Polling.poll { api.queuePosition() }
 *     .every(2.seconds)
 *     .collect(scope) { position -> ui.update(position) }
 * ```
 *
 * See [PollBuilder] for the full set of refinements and terminal verbs.
 */
public object Polling {
    /**
     * Begin describing a poll whose [fetch] returns a plain value each tick (throw to signal an
     * error — it is mapped and run through the retry policy). This is the common entry point.
     */
    public fun <T> poll(fetch: suspend () -> T): PollBuilder<T> =
        PollBuilder { PollingResult.Success(fetch()) }

    /**
     * Advanced entry point for fetches that need the full [PollingResult] vocabulary (e.g.
     * [PollingResult.Waiting] to signal "no value yet, keep polling" without a terminal check).
     */
    public fun <T> pollResult(fetch: suspend () -> PollingResult<T>): PollBuilder<T> =
        PollBuilder(fetch)

    /**
     * Run several polls in order, stopping at the first that does not succeed. Returns the last
     * outcome (the final success, or the first non-success). Replaces the old `compose`.
     */
    public suspend fun <T> sequence(vararg polls: PollBuilder<T>): PollingOutcome<T> =
        PollingEngine.compose(*Array(polls.size) { polls[it].buildConfig() })

    /** Number of polls currently running across the engine (diagnostics). */
    public val activeCount: Int get() = PollingEngine.activePollsCount()

    /** Ids of the polls currently running (diagnostics). */
    public suspend fun activeIds(): List<String> = PollingEngine.listActiveIds()

    /** Cancel every running poll. Individual polls are better cancelled via their `PollHandle`. */
    public suspend fun cancelAll(): Unit = PollingEngine.cancelAll()

    /** Shut the engine down. After shutdown, new polls cannot be started. */
    public suspend fun shutdown(): Unit = PollingEngine.shutdown()
}
