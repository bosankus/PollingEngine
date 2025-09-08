package `in`.androidplay.pollingengine

import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.BackoffPolicy
import `in`.androidplay.pollingengine.polling.Polling
import `in`.androidplay.pollingengine.polling.PollingOutcome
import `in`.androidplay.pollingengine.polling.PollingSession
import `in`.androidplay.pollingengine.polling.RetryPredicates
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.round

data class LogItem(val text: String, val id: Long)

private val logIdCounter = atomic(0L)

data class PollingUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val remainingMs: Long = 0L,
    val logs: List<LogItem> = emptyList(),
    val showProperties: Boolean = false,

    // Editable properties
    val initialDelayText: String = "500",
    val maxDelayText: String = "5000",
    val multiplierText: String = "1.8",
    val jitterText: String = "0.15",
    val maxAttemptsText: String = "12",
    val overallTimeoutText: String = "30000",
    val perAttemptTimeoutText: String = "",

    val retryStrategyIndex: Int = 0,
)

sealed interface PollingIntent {
    data object StartPolling : PollingIntent
    data object PauseOrResumePolling : PollingIntent
    data object StopPolling : PollingIntent
    data object ToggleProperties : PollingIntent
    data class UpdateInitialDelay(val value: String) : PollingIntent
    data class UpdateMaxDelay(val value: String) : PollingIntent
    data class UpdateMultiplier(val value: String) : PollingIntent
    data class UpdateJitter(val value: String) : PollingIntent
    data class UpdateMaxAttempts(val value: String) : PollingIntent
    data class UpdateOverallTimeout(val value: String) : PollingIntent
    data class UpdatePerAttemptTimeout(val value: String) : PollingIntent
    data class UpdateRetryStrategy(val index: Int) : PollingIntent
    data object ApplyBackoffAtRuntime : PollingIntent
}

class PollingViewModel {

    private val _uiState = MutableStateFlow(PollingUiState())
    val uiState: StateFlow<PollingUiState> = _uiState.asStateFlow()

    private val viewModelScope = CoroutineScope(Dispatchers.Default)
    private var pollingJob: Job? = null
    private var countdownJob: Job? = null
    private var pollingSession: PollingSession? = null

    private val retryStrategies = listOf(
        "Always" to RetryPredicates.always,
        "Never" to RetryPredicates.never,
        "Any timeout" to RetryPredicates.networkOrServerOrTimeout
    )

    fun dispatch(intent: PollingIntent) {
        when (intent) {
            is PollingIntent.StartPolling -> startPolling()
            is PollingIntent.PauseOrResumePolling -> pauseOrResumePolling()
            is PollingIntent.StopPolling -> stopPolling()
            is PollingIntent.ToggleProperties -> _uiState.update { it.copy(showProperties = !it.showProperties) }
            is PollingIntent.UpdateInitialDelay -> _uiState.update { it.copy(initialDelayText = intent.value) }
            is PollingIntent.UpdateMaxDelay -> _uiState.update { it.copy(maxDelayText = intent.value) }
            is PollingIntent.UpdateMultiplier -> _uiState.update { it.copy(multiplierText = intent.value) }
            is PollingIntent.UpdateJitter -> _uiState.update { it.copy(jitterText = intent.value) }
            is PollingIntent.UpdateMaxAttempts -> _uiState.update { it.copy(maxAttemptsText = intent.value) }
            is PollingIntent.UpdateOverallTimeout -> _uiState.update { it.copy(overallTimeoutText = intent.value) }
            is PollingIntent.UpdatePerAttemptTimeout -> _uiState.update {
                it.copy(
                    perAttemptTimeoutText = intent.value
                )
            }

            is PollingIntent.UpdateRetryStrategy -> _uiState.update { it.copy(retryStrategyIndex = intent.index) }
            is PollingIntent.ApplyBackoffAtRuntime -> applyBackoffAtRuntime()
        }
    }

    private fun addLog(text: String) {
        _uiState.update {
            it.copy(logs = it.logs + LogItem(text, logIdCounter.incrementAndGet()))
        }
    }

    private fun startPolling() {
        _uiState.update { it.copy(showProperties = false, logs = emptyList()) }

        val currentState = _uiState.value
        val initialDelay = currentState.initialDelayText.toLongOrNull()
        val maxDelay = currentState.maxDelayText.toLongOrNull()
        val multiplier = currentState.multiplierText.toDoubleOrNull()
        val jitter = currentState.jitterText.toDoubleOrNull()
        val maxAttempts = currentState.maxAttemptsText.toIntOrNull()
        val overallTimeout = currentState.overallTimeoutText.toLongOrNull()
        val perAttemptTimeout =
            currentState.perAttemptTimeoutText.trim().ifEmpty { null }?.toLongOrNull()

        if (initialDelay == null || maxDelay == null || multiplier == null || jitter == null || maxAttempts == null || overallTimeout == null || (currentState.perAttemptTimeoutText.isNotEmpty() && perAttemptTimeout == null)) {
            addLog("[error] Invalid properties. Please enter valid numbers.")
            return
        }

        val backoff = BackoffPolicy(
            initialDelayMs = initialDelay,
            maxDelayMs = maxDelay,
            multiplier = multiplier,
            jitterRatio = jitter,
            maxAttempts = maxAttempts,
            overallTimeoutMs = overallTimeout,
            perAttemptTimeoutMs = perAttemptTimeout,
        )

        _uiState.update {
            it.copy(
                isRunning = true,
                isPaused = false,
                remainingMs = backoff.overallTimeoutMs
            )
        }

        var attemptCounter = 0
        pollingJob = Polling.startPolling {
            this.fetch = {
                attemptCounter++
                if (attemptCounter < 8) PollingResult.Waiting else PollingResult.Success("Ready at attempt #$attemptCounter")
            }
            this.isTerminalSuccess = { it.isNotEmpty() }
            this.backoff = backoff
            this.shouldRetryOnError = retryStrategies[_uiState.value.retryStrategyIndex].second
            this.onAttempt = { attempt, delayMs ->
                val baseDelay =
                    (backoff.initialDelayMs * backoff.multiplier.pow((attempt - 1).toDouble())).toLong()
                        .coerceAtMost(backoff.maxDelayMs)
                val baseSecs = ((baseDelay) / 100L).toFloat() / 10f
                val baseSecsStr = ((round(baseSecs * 10f)) / 10f).toString()
                val actualDelay = delayMs ?: 0L
                val actualSecs = (actualDelay / 100L).toFloat() / 10f
                val actualSecsStr = ((round(actualSecs * 10f)) / 10f).toString()
                addLog("[info] Attempt #$attempt (base: ${baseSecsStr}s, actual: ${actualSecsStr}s)")
            }
            this.onResult = { attempt, result ->
                addLog("[info] Result at #$attempt: ${describeResult(result)}")
            }
            this.onComplete = { attempts, durationMs, outcome ->
                val secs = (durationMs / 100L).toFloat() / 10f
                val secsStr = ((round(secs * 10f)) / 10f).toString()
                addLog(
                    "[done] Completed after $attempts attempts in ${secsStr}s: ${
                        describeOutcome(
                            outcome
                        )
                    }"
                )
            }
        }.onEach { outcome ->
            addLog("[done] Final Outcome: ${describeOutcome(outcome)}")
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    remainingMs = 0
                )
            }
        }.launchIn(viewModelScope)

        startCountdown()
    }

    private fun pauseOrResumePolling() {
        val isCurrentlyPaused = _uiState.value.isPaused
        _uiState.update { it.copy(isPaused = !isCurrentlyPaused) }
        pollingSession?.let { session ->
            viewModelScope.launch {
                if (!isCurrentlyPaused) Polling.pause(session.id) else Polling.resume(session.id)
            }
        }
    }

    private fun stopPolling() {
        pollingSession?.let { session ->
            viewModelScope.launch { Polling.cancel(session.id) }
        }
        pollingJob?.cancel()
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                remainingMs = 0
            )
        }
        pollingSession = null
    }

    private fun applyBackoffAtRuntime() {
        val currentState = _uiState.value
        val initialDelay = currentState.initialDelayText.toLongOrNull()
        val maxDelay = currentState.maxDelayText.toLongOrNull()
        val multiplier = currentState.multiplierText.toDoubleOrNull()
        val jitter = currentState.jitterText.toDoubleOrNull()
        val maxAttempts = currentState.maxAttemptsText.toIntOrNull()
        val overallTimeout = currentState.overallTimeoutText.toLongOrNull()
        val perAttemptTimeout =
            currentState.perAttemptTimeoutText.trim().ifEmpty { null }?.toLongOrNull()

        if (initialDelay == null || maxDelay == null || multiplier == null || jitter == null || maxAttempts == null || overallTimeout == null || (currentState.perAttemptTimeoutText.isNotEmpty() && perAttemptTimeout == null)) {
            addLog("[error] Invalid properties; cannot apply backoff.")
            return
        }

        val newPolicy = try {
            BackoffPolicy(
                initialDelayMs = initialDelay,
                maxDelayMs = maxDelay,
                multiplier = multiplier,
                jitterRatio = jitter,
                maxAttempts = maxAttempts,
                overallTimeoutMs = overallTimeout,
                perAttemptTimeoutMs = perAttemptTimeout,
            )
        } catch (t: Throwable) {
            addLog("[error] ${t.message}")
            return
        }

        pollingSession?.let { session ->
            viewModelScope.launch {
                Polling.updateBackoff(session.id, newPolicy)
                addLog("[info] Applied new backoff policy at runtime.")
            }
        } ?: run {
            addLog("[error] Live update not available; stop and start with new settings.")
        }
    }

    private fun startCountdown() {
        countdownJob = viewModelScope.launch {
            while (_uiState.value.isRunning && _uiState.value.remainingMs > 0) {
                kotlinx.coroutines.delay(100)
                if (!_uiState.value.isPaused) {
                    _uiState.update { it.copy(remainingMs = (it.remainingMs - 100).coerceAtLeast(0)) }
                }
            }
        }
    }

    private fun <T> describeResult(result: PollingResult<T>): String = when (result) {
        is PollingResult.Success -> "Success(${result.data})"
        is PollingResult.Failure -> "Failure(code=${result.error.code}, msg=${result.error.message})"
        is PollingResult.Waiting -> "Waiting"
        is PollingResult.Cancelled -> "Cancelled"
        is PollingResult.Unknown -> "Unknown"
    }

    private fun <T> describeOutcome(outcome: PollingOutcome<T>): String = when (outcome) {
        is PollingOutcome.Success -> {
            val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
            "Success(value=${outcome.value}, attempts=${outcome.attempts}, elapsedSec=${((round(secs * 10f)) / 10f)})"
        }

        is PollingOutcome.Exhausted -> {
            val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
            "Exhausted(attempts=${outcome.attempts}, elapsedSec=${((round(secs * 10f)) / 10f)})"
        }

        is PollingOutcome.Timeout -> {
            val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
            "Timeout(attempts=${outcome.attempts}, elapsedSec=${((round(secs * 10f)) / 10f)})"
        }

        is PollingOutcome.Cancelled -> {
            val secs = (outcome.elapsedMs / 100L).toFloat() / 10f
            "Cancelled(attempts=${outcome.attempts}, elapsedSec=${((round(secs * 10f)) / 10f)})"
        }
    }
}
