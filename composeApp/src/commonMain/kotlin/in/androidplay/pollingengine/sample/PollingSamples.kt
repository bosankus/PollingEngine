package `in`.androidplay.pollingengine.sample

import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.BackoffPolicy
import `in`.androidplay.pollingengine.polling.PollingConfig
import `in`.androidplay.pollingengine.polling.PollingEngine
import `in`.androidplay.pollingengine.polling.PollingOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Simple sample APIs showing how to use the polling engine from Android/iOS.
 * - demoPoll(): suspend function, easy to call from Kotlin/Swift (async).
 * - demoPoll(callback): callback-style for legacy Objective-C callers.
 */
object PollingSamples {

    suspend fun demoPoll(): String {
        var attempts = 0
        val config = PollingConfig(
            fetch = {
                attempts++
                when (attempts) {
                    in 1..3 -> PollingResult.Waiting // Simulate not-ready state initially
                    4 -> PollingResult.Success("Server ready on attempt #$attempts")
                    else -> PollingResult.Success("Already ready: attempt #$attempts")
                }
            },
            isTerminalSuccess = { value -> value.isNotEmpty() },
            backoff = BackoffPolicy(
                initialDelayMs = 200,
                maxDelayMs = 1_000,
                multiplier = 1.6,
                jitterRatio = 0.2,
                maxAttempts = 10,
                overallTimeoutMs = 10_000,
            ),
        )

        return when (val outcome = PollingEngine.pollUntil(config)) {
            is PollingOutcome.Success -> outcome.value
            is PollingOutcome.Exhausted -> "Exhausted after ${outcome.attempts} attempts"
            is PollingOutcome.Timeout -> "Timed out after ${outcome.elapsedMs} ms"
            is PollingOutcome.Cancelled -> "Cancelled after ${outcome.attempts} attempts"
        }
    }

    // Callback-based wrapper for Objective-C callers.
    fun demoPoll(callback: (String) -> Unit) {
        GlobalScope.launch(Dispatchers.Default) {
            val result = demoPoll()
            callback(result)
        }
    }
}
