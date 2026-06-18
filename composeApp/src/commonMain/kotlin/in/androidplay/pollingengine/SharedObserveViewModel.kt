package `in`.androidplay.pollingengine

import `in`.androidplay.pollingengine.polling.Polling
import `in`.androidplay.pollingengine.polling.SharedPoll
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Showcases the streaming/multiplexing capabilities of the SDK:
 *  - `Polling.poll { … }.shared(key)`: ONE poll loop per key makes a single fetch per 2s tick and
 *    fans the value out to two independent filtered subscribers (even values / multiples of three).
 *    The upstream fetch counter proves the call is made once per tick regardless of subscriber
 *    count, and `keepAliveFor` stops the loop a few seconds after the last subscriber leaves.
 *  - `Polling.poll { … }.asFlow()`: a continuous stream that emits every successful tick and
 *    auto-completes via a `stopWhen` predicate.
 */
data class SharedObserveUiState(
    // ---- shared multiplexed session ----
    val intervalMs: Long = 2_000,
    val stopTimeoutMs: Long = 3_000,
    val upstreamFetchCount: Int = 0,
    val subscriberA: Boolean = false,
    val subscriberB: Boolean = false,
    val aCount: Int = 0,
    val aLatest: Int? = null,
    val bCount: Int = 0,
    val bLatest: Int? = null,
    // ---- observe continuous stream ----
    val observeRunning: Boolean = false,
    val observeStopAfter: Int = 5,
    val observeValues: List<Int> = emptyList(),
)

sealed interface SharedObserveIntent {
    data object ToggleSubscriberA : SharedObserveIntent
    data object ToggleSubscriberB : SharedObserveIntent
    data object StartObserve : SharedObserveIntent
    data object StopObserve : SharedObserveIntent
}

class SharedObserveViewModel {

    private val _uiState = MutableStateFlow(SharedObserveUiState())
    val uiState: StateFlow<SharedObserveUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    // Unique per instance so re-entering the screen starts a fresh engine session rather than
    // re-attaching to a stale, de-duplicated one bound to a disposed ViewModel.
    private val sessionKey = "demo-services-feed-${instanceCounter.incrementAndGet()}"

    private val fetchCounter = atomic(0)
    private val observeCounter = atomic(0)

    private var session: SharedPoll<Int>? = null
    private var jobA: Job? = null
    private var jobB: Job? = null
    private var observeJob: Job? = null

    fun dispatch(intent: SharedObserveIntent) {
        when (intent) {
            is SharedObserveIntent.ToggleSubscriberA -> toggleA()
            is SharedObserveIntent.ToggleSubscriberB -> toggleB()
            is SharedObserveIntent.StartObserve -> startObserve()
            is SharedObserveIntent.StopObserve -> stopObserve()
        }
    }

    /**
     * Lazily creates the single shared session keyed by [SESSION_KEY]. Repeated calls return the
     * same live session (the engine de-duplicates by key), so both subscribers share one fetch/tick.
     */
    private suspend fun session(): SharedPoll<Int> {
        session?.let { return it }
        val created = Polling.poll {
            val tick = fetchCounter.incrementAndGet()
            _uiState.update { it.copy(upstreamFetchCount = tick) }
            tick
        }
            .every(_uiState.value.intervalMs.milliseconds)
            .keepAliveFor(_uiState.value.stopTimeoutMs.milliseconds)
            .replayLast(1)
            .shared(key = sessionKey)
        session = created
        return created
    }

    private fun toggleA() {
        val running = jobA
        if (running == null) {
            _uiState.update { it.copy(subscriberA = true) }
            jobA = scope.launch {
                session().stream { it % 2 == 0 }
                    .onEach { value ->
                        _uiState.update { it.copy(aLatest = value, aCount = it.aCount + 1) }
                    }
                    .onCompletion { _uiState.update { it.copy(subscriberA = false) } }
                    .collect()
            }
        } else {
            running.cancel()
            jobA = null
            _uiState.update { it.copy(subscriberA = false) }
        }
    }

    private fun toggleB() {
        val running = jobB
        if (running == null) {
            _uiState.update { it.copy(subscriberB = true) }
            jobB = scope.launch {
                session().stream { it % 3 == 0 }
                    .onEach { value ->
                        _uiState.update { it.copy(bLatest = value, bCount = it.bCount + 1) }
                    }
                    .onCompletion { _uiState.update { it.copy(subscriberB = false) } }
                    .collect()
            }
        } else {
            running.cancel()
            jobB = null
            _uiState.update { it.copy(subscriberB = false) }
        }
    }

    private fun startObserve() {
        if (observeJob != null) return
        observeCounter.value = 0
        _uiState.update { it.copy(observeRunning = true, observeValues = emptyList()) }
        observeJob = scope.launch {
            Polling.poll { observeCounter.incrementAndGet() }
                .every(1_000.milliseconds)
                // Auto-complete once the value passes the target (the stopping tick is not emitted).
                .stopWhen { it > _uiState.value.observeStopAfter }
                .asFlow()
                .onEach { value ->
                    _uiState.update { it.copy(observeValues = it.observeValues + value) }
                }
                .onCompletion {
                    _uiState.update { it.copy(observeRunning = false) }
                    observeJob = null
                }
                .collect()
        }
    }

    private fun stopObserve() {
        observeJob?.cancel()
        observeJob = null
        _uiState.update { it.copy(observeRunning = false) }
    }

    /** Cancels all collections; the shared session then stops after its WhileSubscribed grace. */
    fun dispose() {
        scope.cancel()
    }

    private companion object {
        val instanceCounter = atomic(0)
    }
}
