package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.polling.dsl.Retry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the public fluent surface (`Polling.poll { … }`) end to end: the terminal verbs and the
 * [in.androidplay.pollingengine.polling.dsl.PollHandle] control path that replaced id-diffing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PollingDslTest {
    @Test
    fun await_convergesToTerminalSuccess() =
        runTest {
            var calls = 0
            val outcome =
                Polling
                    .poll {
                        calls++
                        "tick-$calls"
                    }.until { it == "tick-3" }
                    .every(10.milliseconds)
                    .on(StandardTestDispatcher(testScheduler))
                    .await()

            assertTrue(outcome is PollingOutcome.Success, "expected Success but was $outcome")
            assertEquals("tick-3", outcome.value)
            assertEquals(3, outcome.attempts)
        }

    @Test
    fun start_handleExposesOutcomeAndId() =
        runTest {
            var calls = 0
            val handle =
                Polling
                    .poll {
                        calls++
                        calls
                    }.until { it >= 2 }
                    .every(10.milliseconds)
                    .on(StandardTestDispatcher(testScheduler))
                    .start(backgroundScope)

            assertTrue(handle.id.isNotEmpty())
            val outcome = handle.outcomes.first()
            assertTrue(outcome is PollingOutcome.Success, "expected Success but was $outcome")
            assertEquals(2, outcome.value)
        }

    @Test
    fun start_pauseFreezesAndResumeContinuesThenCancel() =
        runTest {
            var fetches = 0
            val handle =
                Polling
                    .poll { fetches++ }
                    .every(100.milliseconds) // unbounded cadence, never terminal
                    .on(StandardTestDispatcher(testScheduler))
                    .start(backgroundScope)

            advanceTimeBy(350.milliseconds)
            runCurrent()
            assertTrue(fetches >= 3, "expected several fetches, was $fetches")

            handle.pause()
            // Let any in-flight delay cycle settle into the paused wait, then snapshot.
            advanceTimeBy(300.milliseconds)
            runCurrent()
            val frozen = fetches
            advanceTimeBy(1_000.milliseconds)
            runCurrent()
            assertEquals(frozen, fetches, "paused poll must not keep fetching")
            assertTrue(handle.isPaused)

            handle.resume()
            advanceTimeBy(300.milliseconds)
            runCurrent()
            assertTrue(fetches > frozen, "resumed poll must fetch again")

            handle.cancel()
            runCurrent()
            assertFalse(handle.isActive, "cancelled poll must not be active")
        }

    @Test
    fun collect_deliversEverySuccessValue() =
        runTest {
            var n = 0
            val seen = mutableListOf<Int>()
            Polling
                .poll { ++n }
                .stopWhen { it > 4 } // stop once value passes 4; that tick is not delivered
                .every(10.milliseconds)
                .on(StandardTestDispatcher(testScheduler))
                .collect(backgroundScope) { seen += it }

            advanceTimeBy(200.milliseconds)
            runCurrent()
            assertEquals(listOf(1, 2, 3, 4), seen)
        }

    @Test
    fun sequence_stopsAtFirstNonSuccess() =
        runTest {
            val td = StandardTestDispatcher(testScheduler)
            val first =
                Polling
                    .poll { "a" }
                    .until { true }
                    .every(1.milliseconds)
                    .on(td)
            val second =
                Polling
                    .pollResult<String> {
                        `in`.androidplay.pollingengine.models.PollingResult
                            .Failure(Error(500, "boom"))
                    }.retryWhen(Retry.never)
                    .atMost(1)
                    .every(1.milliseconds)
                    .on(td)

            val outcome = Polling.sequence(first, second)
            assertTrue(outcome is PollingOutcome.Exhausted, "expected Exhausted but was $outcome")
        }

    @Test
    fun atMost_capsAttempts() =
        runTest {
            var calls = 0
            val outcome =
                Polling
                    .poll {
                        calls++
                        "pending"
                    }.until { it == "done" } // never reached
                    .every(1.milliseconds)
                    .atMost(4)
                    .timeout(10.seconds)
                    .on(StandardTestDispatcher(testScheduler))
                    .await()

            assertTrue(outcome is PollingOutcome.Exhausted, "expected Exhausted but was $outcome")
            assertEquals(4, calls)
        }
}
