package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult.Failure
import `in`.androidplay.pollingengine.models.PollingResult.Success
import `in`.androidplay.pollingengine.models.PollingResult.Waiting
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PollingEngineCoreTests {

    @Test
    fun successStopsWhenTerminalReached() = runTest {
        var calls = 0
        val config = PollingConfig(
            fetch = {
                calls++
                if (calls < 3) Success(calls) else Success(100)
            },
            isTerminalSuccess = { it == 100 },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 10,
                overallTimeoutMs = 5_000,
                random = Random(0),
            )
        )

        val outcome = Polling.run(config)
        assertTrue(outcome is PollingOutcome.Success)
        assertEquals(100, outcome.value)
        assertEquals(3, outcome.attempts)
    }

    @Test
    fun nonRetryableFailureEndsAsExhausted() = runTest {
        val config = PollingConfig<Int>(
            fetch = { Failure(Error(999, "boom")) },
            isTerminalSuccess = { false },
            shouldRetryOnError = { false },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 5,
                overallTimeoutMs = 5_000,
            )
        )

        val outcome = Polling.run(config)
        assertTrue(outcome is PollingOutcome.Exhausted)
        assertTrue(outcome.last is Failure)
        assertEquals(1, outcome.attempts)
    }

    @Test
    fun retriesUntilMaxAttemptsWhenRetryable() = runTest {
        var attempts = 0
        val config = PollingConfig<Int>(
            fetch = {
                attempts++
                Failure(Error(1001, "network"))
            },
            isTerminalSuccess = { false },
            shouldRetryOnError = { true },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 3,
                overallTimeoutMs = 5_000,
            )
        )
        val outcome = Polling.run(config)
        assertTrue(outcome is PollingOutcome.Exhausted)
        assertEquals(3, outcome.attempts)
    }

    @Test
    fun overallTimeoutLeadsToTimeoutOutcome() = runTest {
        var calls = 0
        val config = PollingConfig(
            fetch = {
                calls++
                // cause long waits by using Waiting
                Waiting
            },
            isTerminalSuccess = { false },
            backoff = BackoffPolicy(
                initialDelayMs = 10,
                maxDelayMs = 10,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 100,
                overallTimeoutMs = 5 // very small overall timeout
            )
        )
        val outcome = Polling.run(config)
        assertTrue(outcome is PollingOutcome.Timeout, "Expected Timeout but was $outcome")
    }

    @Test
    fun composeStopsOnFirstNonSuccess() = runTest {
        val cfg1 = PollingConfig(
            fetch = { Success(1) },
            isTerminalSuccess = { true },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 1,
                overallTimeoutMs = 100
            )
        )
        val cfg2 = PollingConfig<Int>(
            fetch = { Failure(Error(1, "x")) },
            isTerminalSuccess = { true },
            shouldRetryOnError = { false },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 3,
                overallTimeoutMs = 100
            )
        )
        val outcome = Polling.compose(cfg1, cfg2)
        assertTrue(outcome is PollingOutcome.Exhausted)
        assertEquals(1, outcome.attempts)
    }
}
