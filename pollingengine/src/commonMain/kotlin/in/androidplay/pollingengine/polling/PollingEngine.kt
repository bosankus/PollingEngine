
package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.models.PollingResult.Cancelled
import `in`.androidplay.pollingengine.models.PollingResult.Failure
import `in`.androidplay.pollingengine.models.PollingResult.Success
import `in`.androidplay.pollingengine.models.PollingResult.Unknown
import `in`.androidplay.pollingengine.models.PollingResult.Waiting
import `in`.androidplay.pollingengine.polling.PollingEngine.observe
import `in`.androidplay.pollingengine.polling.PollingEngine.pollUntil
import `in`.androidplay.pollingengine.polling.PollingEngine.shared
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Production-ready polling engine with exponential backoff and jitter.
 * - Coroutine-friendly, supports cancellation.
 * - Robust handling for rogue CancellationException (treat as retryable error if scope is still active).
 * - Configurable via [PollingConfig] and [BackoffPolicy].
 * - Observability hooks (attempt/result/complete).
 * - Supports bounded converge-then-stop runs as well as unbounded, continuous streaming
 *   ([observe]) and multiplexed, subscriber-driven sessions ([shared]).
 */
internal object PollingEngine {
    internal enum class State { Running, Paused }

    internal data class Control(
        val id: String,
        val state: kotlinx.coroutines.flow.MutableStateFlow<State> =
            kotlinx.coroutines.flow.MutableStateFlow(
                State.Running,
            ),
        val backoff: kotlinx.coroutines.flow.MutableStateFlow<BackoffPolicy?> =
            kotlinx.coroutines.flow.MutableStateFlow(
                null,
            ),
    )

    /**
     * Live control surface for a launched poll, returned by [launch]. Wraps the underlying [Job]
     * and the loop's [Control] so a caller can pause/resume/cancel/retune **without** going through
     * the id registry — eliminating the need to resolve a session id after starting.
     */
    internal class EngineSession<T>(
        val id: String,
        private val job: Job,
        private val control: Control,
        /** Emits the single terminal [PollingOutcome] once the loop finishes, then completes. */
        val outcomes: Flow<PollingOutcome<T>>,
    ) {
        val isActive: Boolean get() = job.isActive
        val isPaused: Boolean get() = control.state.value == State.Paused

        fun pause() {
            control.state.value = State.Paused
        }

        fun resume() {
            control.state.value = State.Running
        }

        fun cancel() {
            job.cancel(CancellationException("Cancelled by user"))
        }

        fun retune(policy: BackoffPolicy) {
            control.backoff.value = policy
        }
    }

    private var supervisor: Job = SupervisorJob()
    private var scope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.Default)
    private val mutex = Mutex()
    private val active: MutableMap<String, Job> = mutableMapOf()
    private val controls: MutableMap<String, Control> = mutableMapOf()
    private val sharedSessions: MutableMap<Any, SharedPoll<*>> = mutableMapOf()
    private var isShutdown: Boolean = false

    fun activePollsCount(): Int = active.size

    suspend fun listActiveIds(): List<String> = mutex.withLock { active.keys.toList() }

    /** Cancels all active polls and clears the registry. */
    suspend fun cancelAll() {
        val toCancel: List<Job> = mutex.withLock { active.values.toList() }
        toCancel.forEach { it.cancel(CancellationException("Cancelled by user")) }
        mutex.withLock { active.clear() }
    }

    /** Shuts down the engine: cancels all polls, cancels its scope, and prevents new polls from starting. */
    suspend fun shutdown() {
        if (isShutdown) return
        cancelAll()
        mutex.withLock {
            isShutdown = true
            sharedSessions.clear()
        }
        supervisor.cancel(CancellationException("PollingEngine shutdown"))
    }

    /**
     * Launches a poll into the caller's [scope] and returns an [EngineSession] **synchronously** —
     * the id and control surface exist before the loop runs, so callers never have to resolve a
     * session id after starting. When [onValue] is supplied the loop streams every success value to
     * it (observe mode); otherwise it converges silently. Either way [EngineSession.outcomes] emits
     * the single terminal [PollingOutcome] when the loop ends.
     */
    fun <T> launch(
        scope: CoroutineScope,
        config: PollingConfig<T>,
        onValue: (suspend (T) -> Unit)? = null,
    ): EngineSession<T> {
        if (isShutdown) throw IllegalStateException("PollingEngine is shut down")
        val id = generateId()
        val control = Control(id)
        val deferred = CompletableDeferred<PollingOutcome<T>>()
        val job =
            scope.launch(config.dispatcher) {
                try {
                    mutex.withLock {
                        active[id] = coroutineContext.job
                        controls[id] = control
                    }
                    val outcome = runLoop(config, control) { value -> onValue?.invoke(value) }
                    deferred.complete(outcome)
                } catch (ce: CancellationException) {
                    deferred.cancel(ce)
                    throw ce
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                    throw t
                } finally {
                    mutex.withLock {
                        active.remove(id)
                        controls.remove(id)
                    }
                }
            }
        val outcomes: Flow<PollingOutcome<T>> = flow { emit(deferred.await()) }
        return EngineSession(id, job, control, outcomes)
    }

    /**
     * Continuous streaming mode: emits the value of **every** [PollingResult.Success] tick instead
     * of converging to a single outcome. The flow completes when a terminal condition fires
     * (terminal success, [PollingConfig.stopWhen], a non-retryable failure, attempt/overall limits,
     * or [PollingResult.Cancelled]); per-tick retryable errors are surfaced via the config hooks and
     * skipped. Pairs naturally with an unbounded [BackoffPolicy] (see [BackoffPolicies.fixed]).
     */
    fun <T> observe(config: PollingConfig<T>): Flow<T> =
        channelFlow {
            if (isShutdown) throw IllegalStateException("PollingEngine is shut down")
            val control = Control(generateId())
            runLoop(config, control) { value -> send(value) }
        }

    /**
     * Multiplexed, subscriber-driven session keyed by [key]. The same [key] returns the same live
     * session, so a single underlying poll loop (one [PollingConfig.fetch] per tick) fans its
     * successes out to all subscribers. Polling starts on the first subscriber and stops
     * [stopTimeoutMs] after the last one leaves (WhileSubscribed), replaying the last [replay] values
     * to late subscribers.
     */
    suspend fun <T> shared(
        key: Any,
        config: PollingConfig<T>,
        stopTimeoutMs: Long,
        replay: Int,
    ): SharedPoll<T> {
        if (isShutdown) throw IllegalStateException("PollingEngine is shut down")
        return mutex.withLock {
            @Suppress("UNCHECKED_CAST")
            sharedSessions.getOrPut(key) {
                SharedSessionImpl(key, observe(config), scope, stopTimeoutMs, replay)
            } as SharedPoll<T>
        }
    }

    /**
     * Compose multiple polling operations sequentially. Stops early on non-success outcomes.
     * Returns the last outcome (success from the last config or the first non-success).
     */
    suspend fun <T> compose(vararg configs: PollingConfig<T>): PollingOutcome<T> {
        var lastOutcome: PollingOutcome<T>? = null
        for (cfg in configs) {
            val control = Control(generateId())
            val outcome = pollUntil(cfg, control)
            lastOutcome = outcome
            when (outcome) {
                is PollingOutcome.Success -> continue
                else -> return outcome
            }
        }
        return lastOutcome ?: error("No configs provided")
    }

    private fun generateId(): String {
        val alphabet = (('a'..'z') + ('0'..'9'))
        return buildString(10) { repeat(10) { append(alphabet.random()) } }
    }

    /**
     * Test-only: hosts [shared] sessions on the supplied [testScope] (e.g. a `runTest`
     * `backgroundScope`) so virtual-time schedulers drive their `shareIn` upstreams, and resets
     * the shared-session registry. Not part of the public API.
     */
    internal fun installScopeForTesting(testScope: CoroutineScope) {
        scope = testScope
        isShutdown = false
        sharedSessions.clear()
    }

    internal suspend fun <T> pollUntil(config: PollingConfig<T>): PollingOutcome<T> = pollUntil(config, Control(generateId()))

    private suspend fun <T> pollUntil(
        config: PollingConfig<T>,
        control: Control,
    ): PollingOutcome<T> = runLoop(config, control) { /* converge mode: values are not streamed */ }

    /**
     * Core polling loop shared by the converge ([pollUntil]) and streaming ([observe]) entry points.
     *
     * Each [PollingResult.Success] value is handed to [emit] before the terminal-success check, so
     * streaming callers receive every tick while converge callers pass a no-op. Honors unbounded
     * policies ([BackoffPolicy.isAttemptsUnlimited]/[BackoffPolicy.isOverallTimeoutDisabled]) and the
     * non-success [PollingConfig.stopWhen] terminal.
     */
    private suspend fun <T> runLoop(
        config: PollingConfig<T>,
        control: Control,
        emit: suspend (T) -> Unit,
    ): PollingOutcome<T> =
        withContext(config.dispatcher) {
            val startMark = TimeSource.Monotonic.markNow()
            var attempt = 0
            var nextDelay = config.backoff.initialDelayMs.coerceAtLeast(0L)
            var lastResult: PollingResult<T>? = null

            while (true) {
                ensureActive()
                val policy = control.backoff.value ?: config.backoff

                // Attempt-limit check (skipped when unlimited)
                if (!policy.isAttemptsUnlimited && attempt >= policy.maxAttempts) break
                attempt++

                val elapsedMs = startMark.elapsedNow().inWholeMilliseconds
                val remainingOverall =
                    if (policy.isOverallTimeoutDisabled) Long.MAX_VALUE else policy.overallTimeoutMs - elapsedMs
                if (!policy.isOverallTimeoutDisabled && remainingOverall <= 0) {
                    return@withContext PollingOutcome.Timeout(lastResult, attempt - 1, elapsedMs)
                }

                // Execute one attempt with per-attempt timeout if configured
                val result: PollingResult<T> =
                    try {
                        val timeoutMs = policy.perAttemptTimeoutMs
                        if (timeoutMs != null) {
                            withTimeout(minOf(timeoutMs, remainingOverall).milliseconds) {
                                // Preface only the first attempt immediately
                                if (attempt == 1) {
                                    config.onAttempt(attempt, 0)
                                }
                                config.fetch()
                            }
                        } else {
                            // Preface only the first attempt immediately
                            if (attempt == 1) {
                                config.onAttempt(attempt, 0)
                            }
                            config.fetch()
                        }
                    } catch (ce: CancellationException) {
                        // If scope is not active -> real cancellation; else treat as rogue and convert to Failure
                        if (!isActive) throw ce
                        Failure(config.throwableMapper(ce))
                    } catch (t: Throwable) {
                        Failure(config.throwableMapper(t))
                    }

                config.onResult(attempt, result)
                lastResult = result

                // F4: non-success terminal stop predicate ends polling without a Success outcome.
                if (config.stopWhen(result)) {
                    val totalMs = startMark.elapsedNow().inWholeMilliseconds
                    val outcome = PollingOutcome.Exhausted(result, attempt, totalMs)
                    config.onComplete(attempt, totalMs, outcome)
                    return@withContext outcome
                }

                when (result) {
                    is Success -> {
                        val v = result.data
                        // Stream every success value before deciding on terminal success.
                        emit(v)
                        if (config.isTerminalSuccess(v)) {
                            val totalMs = startMark.elapsedNow().inWholeMilliseconds
                            val outcome = PollingOutcome.Success(v, attempt, totalMs)
                            config.onComplete(attempt, totalMs, outcome)
                            return@withContext outcome
                        }
                        // success but not terminal; continue retrying
                    }

                    is Failure -> {
                        if (!config.shouldRetryOnError(result.error)) {
                            val totalMs = startMark.elapsedNow().inWholeMilliseconds
                            val outcome = PollingOutcome.Exhausted(result, attempt, totalMs)
                            config.onComplete(attempt, totalMs, outcome)
                            return@withContext outcome
                        }
                    }

                    is Cancelled -> {
                        val totalMs = startMark.elapsedNow().inWholeMilliseconds
                        val outcome = PollingOutcome.Cancelled(attempt, totalMs)
                        config.onComplete(attempt, totalMs, outcome)
                        return@withContext outcome
                    }

                    is Waiting, is Unknown -> {
                        // Treat as retryable states
                    }
                }

                // Compute jittered delay around current base (use latest policy)
                val base = nextDelay.coerceAtMost(policy.maxDelayMs)
                val sleepMs = policy.computeJitteredDelay(base).coerceAtMost(policy.maxDelayMs)

                // Increase delay for next cycle using current policy
                val multiplied = (nextDelay.toDouble() * policy.multiplier)
                nextDelay = multiplied.toLong().coerceAtMost(policy.maxDelayMs)

                // Respect overall timeout before sleeping
                val elapsedBeforeSleep = startMark.elapsedNow().inWholeMilliseconds
                val remainingBeforeSleep =
                    if (policy.isOverallTimeoutDisabled) Long.MAX_VALUE else policy.overallTimeoutMs - elapsedBeforeSleep
                if (!policy.isOverallTimeoutDisabled && remainingBeforeSleep <= 0) break

                // Provide the actual computed delay for the NEXT attempt's preface (attempt+1)
                // Only announce if there is time left to sleep and another attempt could happen.
                val nextAttemptIndex = attempt + 1
                if (policy.isAttemptsUnlimited || nextAttemptIndex <= policy.maxAttempts) {
                    config.onAttempt(nextAttemptIndex, sleepMs)
                }

                // Wait for the required delay, pausing countdown if paused
                var remainingDelay = minOf(sleepMs, remainingBeforeSleep)
                val delayStep = 100L // ms granularity for checking pause/resume
                while (remainingDelay > 0) {
                    if (control.state.value == State.Paused) {
                        // Wait until resumed
                        control.state.map { it == State.Running }.first { it }
                    } else {
                        val step = minOf(delayStep, remainingDelay)
                        delay(step.milliseconds)
                        remainingDelay -= step
                    }
                }
            }

            val totalMs = startMark.elapsedNow().inWholeMilliseconds
            val finalPolicy = control.backoff.value ?: config.backoff
            val outcome =
                if (!finalPolicy.isOverallTimeoutDisabled && totalMs >= finalPolicy.overallTimeoutMs) {
                    PollingOutcome.Timeout(lastResult, attempt, totalMs)
                } else {
                    PollingOutcome.Exhausted(lastResult, attempt, totalMs)
                }
            config.onComplete(attempt, totalMs, outcome)
            outcome
        }

    /**
     * [SharedPoll] backed by a single [shareIn]'d upstream, giving one [PollingConfig.fetch]
     * per tick regardless of subscriber count, with WhileSubscribed start/stop semantics.
     */
    private class SharedSessionImpl<T>(
        override val key: Any,
        upstream: Flow<T>,
        scope: CoroutineScope,
        stopTimeoutMs: Long,
        replay: Int,
    ) : SharedPoll<T> {
        private val shared: SharedFlow<T> =
            upstream.shareIn(
                scope = scope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis =
                            stopTimeoutMs.coerceAtLeast(
                                0L,
                            ),
                    ),
                replay = replay.coerceAtLeast(0),
            )

        override fun stream(): Flow<T> = shared

        override fun stream(filter: (T) -> Boolean): Flow<T> = shared.filter(filter)
    }
}
