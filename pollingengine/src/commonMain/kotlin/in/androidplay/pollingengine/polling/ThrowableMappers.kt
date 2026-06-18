package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.polling.ThrowableMappers.networkDefault
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Stock throwable-to-Error mappers for common scenarios. These keep dependencies optional
 * by avoiding direct references to platform-specific or optional libraries.
 *
 * Guidance:
 * - Pair with `Retry.networkOrServer` as a sensible default retry strategy.
 * - Pick one of these mappers based on your environment to translate throwables into retry-friendly codes.
 */
internal object ThrowableMappers {
    /**
     * Default mapper for network-like environments.
     * - TimeoutCancellationException -> TIMEOUT_ERROR_CODE
     * - Otherwise -> NETWORK_ERROR
     */
    val networkDefault: (Throwable) -> Error = { t ->
        val msg = t.message ?: (t::class.simpleName ?: "Throwable")
        when (t) {
            is TimeoutCancellationException -> Error(ErrorCodes.TIMEOUT_ERROR_CODE, msg)
            else -> Error(ErrorCodes.NETWORK_ERROR, msg)
        }
    }

    /**
     * iOS-friendly mapping. Kept equal to [networkDefault] in common code to avoid platform deps.
     */
    val iosDefault: (Throwable) -> Error = networkDefault

    /**
     * Mapper oriented for kotlinx.serialization failures without taking a hard dependency.
     * If the throwable simpleName contains "SerializationException", it is considered a server-side
     * (bad payload) error; otherwise UNKNOWN.
     */
    val kotlinxSerializationDefault: (Throwable) -> Error = { t ->
        val msg = t.message ?: (t::class.simpleName ?: "Throwable")
        val simple = t::class.simpleName ?: ""
        if (simple.contains("SerializationException")) {
            Error(ErrorCodes.SERVER_ERROR_CODE, msg)
        } else {
            Error(ErrorCodes.UNKNOWN_ERROR_CODE, msg)
        }
    }
}
