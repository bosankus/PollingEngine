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
    private var onCompleteHook: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit =
        { _, _, _ -> }
    private var throwableMapper: (Throwable) -> Error = { t ->
        val msg = t.message ?: (t::class.simpleName ?: "Throwable")
        Error(-1, msg)
    }

    /**
     * Sets the suspending fetch operation that produces a PollingResult<T> per attempt.
     * Typical implementation performs I/O and maps responses/errors to domain results.
     */
    public fun fetch(block: suspend () -> PollingResult<T>): PollingConfigBuilder<T> = apply {
        this.fetchStrategy = LambdaFetchStrategy(block)
    }

    internal fun fetch(strategy: FetchStrategy<T>): PollingConfigBuilder<T> = apply {
        this.fetchStrategy = strategy
    }

    /**
     * Sets the terminal success predicate. When it returns true for a Success(value),
     * polling stops with PollingOutcome.Success.
     */
    public fun success(predicate: (T) -> Boolean): PollingConfigBuilder<T> = apply {
        this.successStrategy = LambdaSuccessStrategy(predicate)
    }

    internal fun success(strategy: SuccessStrategy<T>): PollingConfigBuilder<T> = apply {
        this.successStrategy = strategy
    }

    /**
     * Sets the retry predicate used when the last attempt produced a Failure(Error).
     * Return true to retry, false to stop with Exhausted.
     * See built-ins in [RetryPredicates].
     */
    public fun retry(predicate: (Error?) -> Boolean): PollingConfigBuilder<T> = apply {
        this.retryStrategy = LambdaRetryStrategy(predicate)
    }

    internal fun retry(strategy: RetryStrategy): PollingConfigBuilder<T> = apply {
        this.retryStrategy = strategy
    }

    /**
     * Sets the backoff policy controlling delays, jitter, attempts, and timeouts.
     */
    public fun backoff(policy: BackoffPolicy): PollingConfigBuilder<T> = apply {
        this.backoffPolicy = policy
    }

    /**
     * Sets the CoroutineDispatcher used to run polling.
     * Defaults to Dispatchers.Default.
     */
    public fun dispatcher(dispatcher: CoroutineDispatcher): PollingConfigBuilder<T> = apply {
        this.dispatcher = dispatcher
    }

    /**
     * Sets a mapper to convert thrown exceptions into domain Error values.
     * Defaults to a mapper that uses code=-1 and Throwable.message/class name.
     */
    public fun throwableMapper(mapper: (Throwable) -> Error): PollingConfigBuilder<T> = apply {
        this.throwableMapper = mapper
    }


    /**
     * Hook invoked before each attempt is executed, providing the attempt index and the
     * computed delay for the upcoming attempt (null for immediate).
     */
    public fun onAttempt(hook: (attempt: Int, delayMs: Long?) -> Unit): PollingConfigBuilder<T> = apply {
        this.onAttemptHook = hook
    }

    /**
     * Hook invoked after each attempt completes with a result (Success/Waiting/Failure/Unknown/Cancelled).
     */
    public fun onResult(hook: (attempt: Int, result: PollingResult<T>) -> Unit): PollingConfigBuilder<T> = apply {
        this.onResultHook = hook
    }

    /**
     * Hook invoked once with the terminal outcome, total attempts, and elapsed time.
     */
    public fun onComplete(hook: (attempts: Int, durationMs: Long, outcome: PollingOutcome<T>) -> Unit): PollingConfigBuilder<T> =
        apply {
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
            throwableMapper = throwableMapper,
        )
    }
}

/** DSL entrypoint to build a PollingConfig in a concise way. */
public fun <T> pollingConfig(block: PollingConfigBuilder<T>.() -> Unit): PollingConfig<T> =
    PollingConfigBuilder<T>().apply(block).build()
