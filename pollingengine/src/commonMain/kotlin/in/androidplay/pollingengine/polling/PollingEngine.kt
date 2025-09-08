
package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.models.PollingResult.Cancelled
import `in`.androidplay.pollingengine.models.PollingResult.Failure
import `in`.androidplay.pollingengine.models.PollingResult.Success
import `in`.androidplay.pollingengine.models.PollingResult.Unknown
import `in`.androidplay.pollingengine.models.PollingResult.Waiting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeSource

/**
 * Production-ready polling engine with exponential backoff and jitter.
 * - Coroutine-friendly, supports cancellation.
 * - Robust handling for rogue CancellationException (treat as retryable error if scope is still active).
 * - Configurable via [PollingConfig] and [BackoffPolicy].
 * - Observability hooks (attempt/result/complete).
 */
internal object PollingEngine {

    internal enum class State { Running, Paused }

    private data class Control(
        val id: String,
        val state: kotlinx.coroutines.flow.MutableStateFlow<State> = kotlinx.coroutines.flow.MutableStateFlow(
            State.Running
        ),
        val backoff: kotlinx.coroutines.flow.MutableStateFlow<BackoffPolicy?> = kotlinx.coroutines.flow.MutableStateFlow(
            null
        ),
    )

    internal data class Handle(internal val id: String)

    private var supervisor: Job = SupervisorJob()
    private var scope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.Default)
    private val mutex = Mutex()
    private val active: MutableMap<String, Job> = mutableMapOf()
    private val controls: MutableMap<String, Control> = mutableMapOf()
    private var isShutdown: Boolean = false

    fun activePollsCount(): Int = active.size

    suspend fun listActiveIds(): List<String> = mutex.withLock { active.keys.toList() }

    suspend fun cancel(id: String) {
        mutex.withLock { active[id]?.cancel(CancellationException("Cancelled by user")) }
    }

    suspend fun pause(id: String) {
        mutex.withLock { controls[id]?.state?.value = State.Paused }
    }

    suspend fun resume(id: String) {
        mutex.withLock { controls[id]?.state?.value = State.Running }
    }

    suspend fun updateBackoff(id: String, newPolicy: BackoffPolicy) {
        mutex.withLock { controls[id]?.backoff?.value = newPolicy }
    }

    suspend fun cancel(handle: Handle) {
        cancel(handle.id)
    }

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
        }
        supervisor.cancel(CancellationException("PollingEngine shutdown"))
    }

    fun <T> startPolling(
        config: PollingConfig<T>
    ): Flow<PollingOutcome<T>> = channelFlow {
        if (isShutdown) throw IllegalStateException("PollingEngine is shut down")
        val id = generateId()
        val control = Control(id)
        val job = scope.launch(config.dispatcher) {
            val outcome = pollUntil(config, control)
            try {
                send(outcome)
                close()
            } finally {
                mutex.withLock {
                    active.remove(id)
                    controls.remove(id)
                }
            }
        }
        mutex.withLock {
            active[id] = job
            controls[id] = control
        }
        awaitClose {
            job.cancel()
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

    internal suspend fun <T> pollUntil(config: PollingConfig<T>): PollingOutcome<T> =
        pollUntil(config, Control(generateId()))

    private suspend fun <T> pollUntil(
        config: PollingConfig<T>,
        control: Control
    ): PollingOutcome<T> = withContext(config.dispatcher) {
        val startMark = TimeSource.Monotonic.markNow()
        var attempt = 0
        var nextDelay = config.backoff.initialDelayMs.coerceAtLeast(0L)
        var lastResult: PollingResult<T>? = null

        try {
            while (attempt < (control.backoff.value ?: config.backoff).maxAttempts) {
                ensureActive()
                attempt++

                val policy = control.backoff.value ?: config.backoff
                val elapsedMs = startMark.elapsedNow().inWholeMilliseconds
                val remainingOverall = policy.overallTimeoutMs - elapsedMs
                if (remainingOverall <= 0) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext PollingOutcome.Timeout(lastResult, attempt - 1, elapsedMs)
                }

                // Execute one attempt with per-attempt timeout if configured
                val result: PollingResult<T> = try {
                    val timeoutMs = policy.perAttemptTimeoutMs
                    if (timeoutMs != null) {
                        withTimeout(minOf(timeoutMs, remainingOverall)) {
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
                    if (!this.isActive) throw ce
                    Failure(config.throwableMapper(ce))
                } catch (t: Throwable) {
                    Failure(config.throwableMapper(t))
                }

                config.onResult(attempt, result)
                lastResult = result

                when (result) {
                    is Success -> {
                        val v = result.data
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
                            @Suppress("UNCHECKED_CAST")
                            config.onComplete(attempt, totalMs, outcome as PollingOutcome<T>)
                            @Suppress("UNCHECKED_CAST")
                            return@withContext (outcome as PollingOutcome<T>)
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
                val remainingBeforeSleep = policy.overallTimeoutMs - elapsedBeforeSleep
                if (remainingBeforeSleep <= 0) break

                // Provide the actual computed delay for the NEXT attempt's preface (attempt+1)
                // Only announce if there is time left to sleep and another attempt could happen.
                val nextAttemptIndex = attempt + 1
                if (nextAttemptIndex <= policy.maxAttempts) {
                    config.onAttempt(nextAttemptIndex, sleepMs)
                }

                // Suspend while paused
                if (control.state.value == State.Paused) {
                    control.state.map { it == State.Running }.first { it }
                }

                delay(minOf(sleepMs, remainingBeforeSleep))
            }

            val totalMs = startMark.elapsedNow().inWholeMilliseconds
            val outcome =
                if (totalMs >= (control.backoff.value ?: config.backoff).overallTimeoutMs) {
                PollingOutcome.Timeout(lastResult, attempt, totalMs)
            } else {
                PollingOutcome.Exhausted(lastResult, attempt, totalMs)
            }
            config.onComplete(attempt, totalMs, outcome)
            outcome
        } finally {
            // no-op; lifecycle handled in startPolling
        }
    }
}
