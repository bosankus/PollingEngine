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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
 * - Observability hooks and metrics (optional).
 */
public object PollingEngine {

    public data class Handle(public val id: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val active: MutableMap<String, Job> = mutableMapOf()

    public fun activePollsCount(): Int = active.size

    public suspend fun listActiveIds(): List<String> = mutex.withLock { active.keys.toList() }

    public suspend fun cancel(id: String) {
        mutex.withLock { active[id]?.cancel(CancellationException("Cancelled by user")) }
    }

    public suspend fun cancel(handle: Handle) {
        cancel(handle.id)
    }

    public fun <T> startPolling(
        config: PollingConfig<T>,
        onComplete: (PollingOutcome<T>) -> Unit,
    ): Handle {
        val id = generateId()
        val job = scope.launch(config.dispatcher) {
            val outcome = pollUntil(config)
            try {
                onComplete(outcome)
            } finally {
                mutex.withLock { active.remove(id) }
            }
        }
        scope.launch { mutex.withLock { active[id] = job } }
        return Handle(id)
    }

    private fun generateId(): String {
        val alphabet = (('a'..'z') + ('0'..'9'))
        return buildString(10) { repeat(10) { append(alphabet.random()) } }
    }

    public suspend fun <T> pollUntil(config: PollingConfig<T>): PollingOutcome<T> = withContext(config.dispatcher) {
        val startMark = TimeSource.Monotonic.markNow()
        var attempt = 0
        var nextDelay = config.backoff.initialDelayMs.coerceAtLeast(0L)
        var lastResult: PollingResult<T>? = null

        try {
            while (attempt < config.backoff.maxAttempts) {
                ensureActive()
                attempt++

                val elapsedMs = startMark.elapsedNow().inWholeMilliseconds
                val remainingOverall = config.backoff.overallTimeoutMs - elapsedMs
                if (remainingOverall <= 0) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext PollingOutcome.Timeout(lastResult, attempt - 1, elapsedMs)
                }

                // Execute one attempt with per-attempt timeout if configured
                val result: PollingResult<T> = try {
                    val timeoutMs = config.backoff.perAttemptTimeoutMs
                    if (timeoutMs != null) {
                        withTimeout(minOf(timeoutMs, remainingOverall)) {
                            // Preface only the first attempt immediately
                            if (attempt == 1) {
                                config.metrics?.recordAttempt(attempt, 0)
                                config.onAttempt(attempt, 0)
                            }
                            config.fetch()
                        }
                    } else {
                        // Preface only the first attempt immediately
                        if (attempt == 1) {
                            config.metrics?.recordAttempt(attempt, 0)
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

                config.metrics?.recordResult(attempt, result)
                config.onResult(attempt, result)
                lastResult = result

                when (result) {
                    is Success -> {
                        val v = result.data
                        if (config.isTerminalSuccess(v)) {
                            val totalMs = startMark.elapsedNow().inWholeMilliseconds
                            val outcome = PollingOutcome.Success(v, attempt, totalMs)
                            config.metrics?.recordComplete(attempt, totalMs)
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
                            config.metrics?.recordComplete(attempt, totalMs)
                            @Suppress("UNCHECKED_CAST")
                            config.onComplete(attempt, totalMs, outcome as PollingOutcome<T>)
                            @Suppress("UNCHECKED_CAST")
                            return@withContext (outcome as PollingOutcome<T>)
                        }
                    }

                    is Cancelled -> {
                        val totalMs = startMark.elapsedNow().inWholeMilliseconds
                        val outcome = PollingOutcome.Cancelled(attempt, totalMs)
                        config.metrics?.recordComplete(attempt, totalMs)
                        config.onComplete(attempt, totalMs, outcome)
                        return@withContext outcome
                    }

                    is Waiting, is Unknown -> {
                        // Treat as retryable states
                    }
                }

                // Compute jittered delay around current base
                val base = nextDelay.coerceAtMost(config.backoff.maxDelayMs)
                val sleepMs = config.backoff.computeJitteredDelay(base).coerceAtMost(config.backoff.maxDelayMs)

                // Increase delay for next cycle
                val multiplied = (nextDelay.toDouble() * config.backoff.multiplier)
                nextDelay = multiplied.toLong().coerceAtMost(config.backoff.maxDelayMs)

                // Respect overall timeout before sleeping
                val elapsedBeforeSleep = startMark.elapsedNow().inWholeMilliseconds
                val remainingBeforeSleep = config.backoff.overallTimeoutMs - elapsedBeforeSleep
                if (remainingBeforeSleep <= 0) break

                // Provide the actual computed delay for the NEXT attempt's preface (attempt+1)
                // Only announce if there is time left to sleep and another attempt could happen.
                val nextAttemptIndex = attempt + 1
                if (nextAttemptIndex <= config.backoff.maxAttempts) {
                    config.metrics?.recordAttempt(nextAttemptIndex, sleepMs)
                    config.onAttempt(nextAttemptIndex, sleepMs)
                }

                delay(minOf(sleepMs, remainingBeforeSleep))
            }

            val totalMs = startMark.elapsedNow().inWholeMilliseconds
            val outcome = if (totalMs >= config.backoff.overallTimeoutMs) {
                PollingOutcome.Timeout(lastResult, attempt, totalMs)
            } else {
                PollingOutcome.Exhausted(lastResult, attempt, totalMs)
            }
            config.metrics?.recordComplete(attempt, totalMs)
            config.onComplete(attempt, totalMs, outcome)
            outcome
        } finally {
            // no-op; lifecycle handled in startPolling
        }
    }
}
