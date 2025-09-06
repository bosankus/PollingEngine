package `in`.androidplay.pollingengine.polling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PollingEngineCancellationTest {
    @Test
    fun rogueCancellationException_isTreatedAsFailureAndLeadsToExhausted() = runTest {
        val config = PollingConfig<Unit>(
            fetch = {
                // Throw a CancellationException while the scope is active
                throw CancellationException("rogue cancel")
            },
            isTerminalSuccess = { false },
            shouldRetryOnError = { false }, // do not retry on mapped error
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = 1, // single attempt
                overallTimeoutMs = 5_000,
                perAttemptTimeoutMs = null,
            )
        )

        val outcome = Polling.run(config)
        assertTrue(outcome is PollingOutcome.Exhausted, "Expected Exhausted outcome when CancellationException is thrown under active scope, but was: $outcome")
    }
}
