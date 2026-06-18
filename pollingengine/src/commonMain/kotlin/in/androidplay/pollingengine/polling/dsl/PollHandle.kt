package `in`.androidplay.pollingengine.polling.dsl

import `in`.androidplay.pollingengine.polling.PollingEngine.EngineSession
import `in`.androidplay.pollingengine.polling.PollingOutcome
import kotlinx.coroutines.flow.Flow

/**
 * A live handle to a poll started with [PollBuilder.start] or [PollBuilder.collect].
 *
 * It carries the running poll's [id] and lets you [pause], [resume], [cancel], or [retune] it
 * directly — no need to look the session up by id after starting. Collect [outcomes] to await the
 * terminal result of a converging poll.
 *
 * ```
 * val poll = Polling.poll { api.status() }.until { it == "DONE" }.every(2.seconds).start(scope)
 * poll.pause(); poll.resume(); poll.cancel()
 * ```
 */
public interface PollHandle<T> {
    /** Stable id of the underlying poll, useful for logging/diagnostics. */
    public val id: String

    /** Emits the single terminal [PollingOutcome] when the poll finishes, then completes. */
    public val outcomes: Flow<PollingOutcome<T>>

    /** `true` while the poll is still running. */
    public val isActive: Boolean

    /** `true` while the poll is paused. */
    public val isPaused: Boolean

    /** Pause the poll loop; the countdown to the next attempt freezes until [resume]. */
    public suspend fun pause()

    /** Resume a paused poll. */
    public suspend fun resume()

    /** Cancel the poll. The [outcomes] flow is cancelled with it. */
    public suspend fun cancel()

    /** Hot-swap the backoff for the running poll (e.g. slow down once a value is close). */
    public fun retune(backoff: BackoffSpec.() -> Unit)
}

internal class EngineSessionHandle<T>(
    private val session: EngineSession<T>,
) : PollHandle<T> {
    override val id: String get() = session.id
    override val outcomes: Flow<PollingOutcome<T>> get() = session.outcomes
    override val isActive: Boolean get() = session.isActive
    override val isPaused: Boolean get() = session.isPaused

    override suspend fun pause(): Unit = session.pause()
    override suspend fun resume(): Unit = session.resume()
    override suspend fun cancel(): Unit = session.cancel()
    override fun retune(backoff: BackoffSpec.() -> Unit) {
        session.retune(BackoffSpec().apply(backoff).toPolicy())
    }
}
