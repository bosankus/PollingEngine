package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.PollingResult.Success
import `in`.androidplay.pollingengine.models.PollingResult.Waiting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Covers the continuous / multiplexed / unbounded polling features (F1–F6).
 * All timing is driven by `runTest` virtual time via a [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PollingStreamingTest {

    // F1 — Unbounded polling: runs well past the default 8-attempt cap with no overall timeout.
    @Test
    fun f1_unboundedRun_runsPastDefaultAttemptCap() = runTest {
        var calls = 0
        val config = PollingConfig(
            fetch = {
                calls++
                if (calls >= 50) Success(calls) else Waiting
            },
            isTerminalSuccess = { it >= 50 },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = BackoffPolicy.UNLIMITED_ATTEMPTS,
                overallTimeoutMs = BackoffPolicy.NO_TIMEOUT,
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val outcome = PollingEngine.pollUntil(config)
        assertTrue(outcome is PollingOutcome.Success, "expected Success but was $outcome")
        assertEquals(50, outcome.attempts)
    }

    // F2 — Fixed-interval scheduling: constant cadence, no growth, no jitter.
    @Test
    fun f2_fixedInterval_hasConstantCadence() = runTest {
        val announced = mutableListOf<Long?>()
        val config = PollingConfig(
            fetch = { Waiting },
            isTerminalSuccess = { false },
            backoff = BackoffPolicies.fixed(intervalMs = 1_000).copy(maxAttempts = 4),
            dispatcher = StandardTestDispatcher(testScheduler),
            onAttempt = { _, delayMs -> announced.add(delayMs) },
        )

        val outcome = PollingEngine.pollUntil(config)
        assertTrue(outcome is PollingOutcome.Exhausted, "expected Exhausted but was $outcome")
        assertEquals(0L, announced.first(), "first attempt should be immediate")
        val cadence = announced.drop(1)
        assertTrue(cadence.isNotEmpty(), "expected scheduled attempts")
        assertTrue(cadence.all { it == 1_000L }, "expected constant 1000ms cadence, was $cadence")
    }

    // F3 — Continuous streaming: every success value is emitted, indefinitely.
    @Test
    fun f3_observe_emitsEverySuccessTick() = runTest {
        var n = 0
        val config = PollingConfig(
            fetch = {
                n++
                Success(n)
            },
            isTerminalSuccess = { false },
            backoff = unboundedNoDelay(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val values = PollingEngine.observe(config).take(5).toList()

        assertEquals(listOf(1, 2, 3, 4, 5), values)
    }

    // F4 — Stop predicate (streaming): empty result stops the stream without emitting it.
    @Test
    fun f4_observe_stopsOnEmptyWithoutEmitting() = runTest {
        var tick = 0
        val config = PollingConfig<List<Int>>(
            fetch = {
                tick++
                if (tick <= 2) Success(listOf(tick)) else Success(emptyList())
            },
            isTerminalSuccess = { false },
            stopWhen = { it is Success && it.data.isEmpty() },
            backoff = unboundedNoDelay(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val values = PollingEngine.observe(config).toList()

        assertEquals(listOf(listOf(1), listOf(2)), values)
    }

    // F4 — Stop predicate (converge): empty result yields Exhausted carrying the stopping result.
    @Test
    fun f4_run_stopWhenEmpty_yieldsExhausted() = runTest {
        var tick = 0
        val config = PollingConfig<List<Int>>(
            fetch = {
                tick++
                if (tick <= 1) Success(listOf(1)) else Success(emptyList())
            },
            isTerminalSuccess = { false },
            backoff = BackoffPolicy(
                initialDelayMs = 0,
                maxDelayMs = 1,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = BackoffPolicy.UNLIMITED_ATTEMPTS,
                overallTimeoutMs = BackoffPolicy.NO_TIMEOUT,
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
            stopWhen = { it is Success && it.data.isEmpty() },
        )

        val outcome = PollingEngine.pollUntil(config)
        assertTrue(outcome is PollingOutcome.Exhausted, "expected Exhausted but was $outcome")
        val last = outcome.last
        assertTrue(
            last is Success && (last.data as List<*>).isEmpty(),
            "expected empty Success as last result"
        )
    }

    // F5 — Multiplexed sessions: one fetch per tick, fanned out to subscribers with distinct filters.
    @Test
    fun f5_shared_singleFetchPerTick_distinctFilters() = runTest {
        PollingEngine.installScopeForTesting(backgroundScope)
        var fetches = 0
        val config = PollingConfig(
            fetch = {
                fetches++
                Success(fetches)
            },
            isTerminalSuccess = { false },
            backoff = BackoffPolicy(
                initialDelayMs = 10,
                maxDelayMs = 10,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = BackoffPolicy.UNLIMITED_ATTEMPTS,
                overallTimeoutMs = BackoffPolicy.NO_TIMEOUT,
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val session = PollingEngine.shared(key = "f5", config = config, stopTimeoutMs = 0, replay = 0)

        val all = async { session.stream().take(3).toList() }
        val even = async { session.stream { it % 2 == 0 }.take(2).toList() }

        val allList = all.await()
        val evenList = even.await()

        assertEquals(listOf(1, 2, 3), allList, "raw view should see every consecutive tick value")
        assertEquals(
            listOf(2, 4),
            evenList,
            "filtered view sees only its matches of the same upstream"
        )
        // Two subscribers, but at most one fetch per tick (4 ticks reach value 4); never doubled.
        assertTrue(fetches in 4..5, "expected ~one fetch per tick, was $fetches")
    }

    // F6 — Subscriber-driven lifecycle: starts on first subscriber, stops after the grace period.
    @Test
    fun f6_shared_startsOnSubscribe_stopsAfterGrace() = runTest {
        PollingEngine.installScopeForTesting(backgroundScope)
        var fetches = 0
        val config = PollingConfig(
            fetch = {
                fetches++
                Success(fetches)
            },
            isTerminalSuccess = { false },
            backoff = BackoffPolicy(
                initialDelayMs = 100,
                maxDelayMs = 100,
                multiplier = 1.0,
                jitterRatio = 0.0,
                maxAttempts = BackoffPolicy.UNLIMITED_ATTEMPTS,
                overallTimeoutMs = BackoffPolicy.NO_TIMEOUT,
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val session =
            PollingEngine.shared(key = "f6", config = config, stopTimeoutMs = 500, replay = 0)

        // No subscriber yet -> no polling has started.
        assertEquals(0, fetches)

        val collected = session.stream().take(2).toList()
        assertEquals(listOf(1, 2), collected)

        // Advance far beyond the 500ms grace; polling should have stopped.
        advanceTimeBy(5_000.milliseconds)
        runCurrent()
        val afterGrace = fetches
        advanceTimeBy(5_000.milliseconds)
        runCurrent()
        assertEquals(
            afterGrace,
            fetches,
            "polling must stop once grace elapses with no subscribers"
        )
        assertTrue(afterGrace >= 2)
    }

    private fun unboundedNoDelay(): BackoffPolicy = BackoffPolicy(
        initialDelayMs = 0,
        maxDelayMs = 1,
        multiplier = 1.0,
        jitterRatio = 0.0,
        maxAttempts = BackoffPolicy.UNLIMITED_ATTEMPTS,
        overallTimeoutMs = BackoffPolicy.NO_TIMEOUT,
    )
}
