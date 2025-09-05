package `in`.androidplay.pollingengine.polling

import `in`.androidplay.pollingengine.models.Error
import `in`.androidplay.pollingengine.models.PollingResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Builder Pattern for creating PollingConfig in a fluent, readable way.
 * Keeps compatibility by producing the existing PollingConfig<T>.
 */
public class PollingConfigBuilder<T> {
    private var fetchStrategy: FetchStrategy<T>? = null
    private var successStrategy: SuccessStrategy<T>? = null
    private var retryStrategy: RetryStrategy = LambdaRetryStrategy { true }
    private var backoffPolicy: BackoffPolicy = BackoffPolicy()
    private var dispatcher: CoroutineDispatcher = Dispatchers.Default
    private var onAttemptHook: (attempt: Int, delayMs: Long?) -> Unit = { _, _ -> }
    private var onResultHook: (attempt: Int, result: PollingResult<T>) -> Unit = { _, _ -> }
    private var onCompleteHook: (attempts: Int, durationMs: Long, outcome: PollingOutcome<out T>) -> Unit = { _, _, _ -> }

    public fun fetch(block: suspend () -> PollingResult<T>): PollingConfigBuilder<T> = apply {
        this.fetchStrategy = LambdaFetchStrategy(block)
    }

    public fun fetch(strategy: FetchStrategy<T>): PollingConfigBuilder<T> = apply {
        this.fetchStrategy = strategy
    }

    public fun success(predicate: (T) -> Boolean): PollingConfigBuilder<T> = apply {
        this.successStrategy = LambdaSuccessStrategy(predicate)
    }

    public fun success(strategy: SuccessStrategy<T>): PollingConfigBuilder<T> = apply {
        this.successStrategy = strategy
    }

    public fun retry(predicate: (Error?) -> Boolean): PollingConfigBuilder<T> = apply {
        this.retryStrategy = LambdaRetryStrategy(predicate)
    }

    public fun retry(strategy: RetryStrategy): PollingConfigBuilder<T> = apply {
        this.retryStrategy = strategy
    }

    public fun backoff(policy: BackoffPolicy): PollingConfigBuilder<T> = apply {
        this.backoffPolicy = policy
    }

    public fun dispatcher(dispatcher: CoroutineDispatcher): PollingConfigBuilder<T> = apply {
        this.dispatcher = dispatcher
    }

    public fun onAttempt(hook: (attempt: Int, delayMs: Long?) -> Unit): PollingConfigBuilder<T> = apply {
        this.onAttemptHook = hook
    }

    public fun onResult(hook: (attempt: Int, result: PollingResult<T>) -> Unit): PollingConfigBuilder<T> = apply {
        this.onResultHook = hook
    }

    public fun onComplete(hook: (attempts: Int, durationMs: Long, outcome: PollingOutcome<out T>) -> Unit): PollingConfigBuilder<T> = apply {
        this.onCompleteHook = hook
    }

    public fun build(): PollingConfig<T> {
        val fetchFn = checkNotNull(fetchStrategy) { "fetch strategy must be provided" }
        val successFn = checkNotNull(successStrategy) { "success strategy must be provided" }

        return PollingConfig(
            fetch = { fetchFn.fetch() },
            isTerminalSuccess = { value -> successFn.isTerminal(value) },
            shouldRetryOnError = { error -> retryStrategy.shouldRetry(error) },
            backoff = backoffPolicy,
            dispatcher = dispatcher,
            onAttempt = onAttemptHook,
            onResult = onResultHook,
            onComplete = onCompleteHook,
        )
    }
}
