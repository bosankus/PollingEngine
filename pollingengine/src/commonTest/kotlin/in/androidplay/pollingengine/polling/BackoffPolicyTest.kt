package `in`.androidplay.pollingengine.polling

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackoffPolicyTest {
    @Test
    fun invalidParameters_throwIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(initialDelayMs = -1) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(maxDelayMs = 0) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(overallTimeoutMs = 0) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(multiplier = 0.9) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(jitterRatio = -0.1) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(jitterRatio = 1.1) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(perAttemptTimeoutMs = 0) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy(initialDelayMs = 2000, maxDelayMs = 1000) }
    }

    @Test
    fun jitteredDelay_withinExpectedBounds() {
        val base = 1_000L
        val jitter = 0.2
        val policy = BackoffPolicy(
            initialDelayMs = base,
            maxDelayMs = 10_000,
            multiplier = 2.0,
            jitterRatio = jitter,
            random = Random(0)
        )
        val minBound = (base * (1 - jitter)).toLong()
        val maxBound = (base * (1 + jitter)).toLong()
        repeat(100) {
            val v = policy.computeJitteredDelay(base)
            assertTrue(v in minBound..maxBound, "Value $v not in [$minBound, $maxBound]")
        }
    }
}