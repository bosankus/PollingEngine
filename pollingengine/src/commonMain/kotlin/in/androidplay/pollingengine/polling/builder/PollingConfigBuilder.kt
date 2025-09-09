package `in`.androidplay.pollingengine.polling.builder

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult
import `in`.androidplay.pollingengine.polling.BackoffPolicy
import `in`.androidplay.pollingengine.polling.PollingConfig
import `in`.androidplay.pollingengine.polling.PollingOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@DslMarker
public annotation class PollingBuilderMarker

@PollingBuilderMarker
public class PollingConfigBuilder<T> {

    public var fetch: (suspend () -> PollingResult<T>)? = null
    public var isTerminalSuccess: ((T) -> Boolean)? = null
    public var shouldRetryOnError: (Error?) -> Boolean = { true }
    public var backoff: BackoffPolicy = BackoffPolicy()
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
    public var onAttempt: (attempt: Int, delayMs: Long?) -> Unit = { _, _ -> }
    public var onResult: (attempt: Int, result: PollingResult<T>) -> Unit = { _, _ -> }
    public var onComplete: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit =
        { _, _, _ -> }
    public var throwableMapper: (Throwable) -> Error = { t ->
        val msg = t.message ?: (t::class.simpleName ?: "Throwable")
        Error(-1, msg)
    }

    public fun build(): PollingConfig<T> {
        return PollingConfig(
            fetch = fetch ?: throw IllegalStateException("fetch must be set"),
            isTerminalSuccess = isTerminalSuccess
                ?: throw IllegalStateException("isTerminalSuccess must be set"),
            shouldRetryOnError = shouldRetryOnError,
            backoff = backoff,
            dispatcher = dispatcher,
            onAttempt = onAttempt,
            onResult = onResult,
            onComplete = onComplete,
            throwableMapper = throwableMapper
        )
    }
}
