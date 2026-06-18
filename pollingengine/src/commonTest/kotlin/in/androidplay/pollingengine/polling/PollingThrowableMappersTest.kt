package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private class SerializationException : Exception()

class PollingThrowableMappersTest {
    @Test
    fun networkDefault_mapsToRetryableCodes() =
        runTest {
            val timeoutThrowable: Throwable =
                try {
                    withTimeout(1.milliseconds) { delay(10.milliseconds) }
                    AssertionError("Expected timeout not thrown")
                } catch (t: Throwable) {
                    t
                }
            val timeoutErr: Error = ThrowableMappers.networkDefault(timeoutThrowable)
            assertTrue(
                RetryPredicates.networkOrServerOrTimeout(timeoutErr),
                "Timeout should be considered retryable",
            )

            val ioLikeErr: Error = ThrowableMappers.networkDefault(IllegalStateException("io"))
            assertTrue(
                RetryPredicates.networkOrServerOrTimeout(ioLikeErr),
                "Network-like error should be considered retryable",
            )
        }

    @Test
    fun kotlinxSerializationDefault_mapsToRetryableCodes() {
        val serErr: Error = ThrowableMappers.kotlinxSerializationDefault(SerializationException())
        assertTrue(
            RetryPredicates.networkOrServerOrTimeout(serErr),
            "Serialization error should be considered retryable",
        )

        val unknownErr: Error =
            ThrowableMappers.kotlinxSerializationDefault(IllegalArgumentException("x"))
        assertTrue(
            RetryPredicates.networkOrServerOrTimeout(unknownErr),
            "Unknown error should still be considered retryable by default predicate",
        )
    }

    @Test
    fun iosDefault_isRetryableForTimeouts() =
        runTest {
            val timeoutThrowable: Throwable =
                try {
                    withTimeout(1.milliseconds) { delay(10.milliseconds) }
                    AssertionError("Expected timeout not thrown")
                } catch (t: Throwable) {
                    t
                }
            val timeoutErr: Error = ThrowableMappers.iosDefault(timeoutThrowable)
            assertTrue(RetryPredicates.networkOrServerOrTimeout(timeoutErr))
        }
}
