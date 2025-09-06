# PollingEngine — Developer Guide

Last updated: 2025-09-07 02:33

This guide explains the public API exposed to app developers and how to use it from Android/iOS. The
library provides a robust polling engine with exponential backoff, retry predicates, and runtime
controls.

Key concepts:

- Polling facade (Polling): the single entry point apps use.
- PollingApi: the public interface implemented by Polling.
- PollingConfig DSL: declare what to fetch, how to detect success, and how to back off.
- BackoffPolicy: controls delays, jitter, attempts, and timeouts.
- Outcomes: PollingOutcome<T> is the terminal result (Success, Exhausted, Timeout, Cancelled).

## Quick start

Create a config and run once (suspending):

```kotlin
import in .androidplay.pollingengine.models.PollingResult
import in .androidplay.pollingengine.polling.*

val config = pollingConfig<String> {
    fetch {
        // Return a PollingResult based on your current state/network
        // e.g. call server and map response to PollingResult
        PollingResult.Waiting // or Success(data), Failure(error), Unknown, Cancelled
    }
    success { data -> data == "READY" }
    // Choose a retry predicate (see RetryPredicates below)
    retry(RetryPredicates.networkOrServerOrTimeout)
    backoff(BackoffPolicies.quick20s)
}

suspend fun runOnce(): PollingOutcome<String> = Polling.run(config)
```

Start background polling with a callback and control it later:

```kotlin
val handle = Polling.startPolling(config) { outcome ->
    // Called when polling reaches a terminal outcome
    println("Outcome: $outcome")
}

// Pause/Resume
kotlinx.coroutines.GlobalScope.launch { Polling.pause(handle.id) }
kotlinx.coroutines.GlobalScope.launch { Polling.resume(handle.id) }

// Update backoff at runtime
kotlinx.coroutines.GlobalScope.launch {
    Polling.updateBackoff(handle.id, BackoffPolicies.quick20s)
}

// Cancel
kotlinx.coroutines.GlobalScope.launch { Polling.cancel(handle) }
```

## API surface (stable)

Polling facade implements the following interface:

```kotlin
interface PollingApi {
    fun activePollsCount(): Int
    suspend fun listActiveIds(): List<String>

    suspend fun cancel(id: String)
    suspend fun cancel(session: in.androidplay.pollingengine.polling.PollingSession)
    suspend fun cancelAll()
    suspend fun shutdown()

    suspend fun pause(id: String)
    suspend fun resume(id: String)
    suspend fun updateBackoff(id: String, newPolicy: BackoffPolicy)

    fun <T> startPolling(
        config: PollingConfig<T>,
        onComplete: (PollingOutcome<T>) -> Unit,
    ): in.androidplay.pollingengine.polling.PollingSession

    suspend fun <T> run(config: PollingConfig<T>): PollingOutcome<T>
    suspend fun <T> compose(vararg configs: PollingConfig<T>): PollingOutcome<T>
}
```

Use Polling everywhere in apps; Polling delegates to the internal PollingEngine implementation.

## DSL overview (PollingConfig)

```kotlin
val config = pollingConfig<MyType> {
    fetch {
        // Do work and return a PollingResult<MyType>
        // Success(value), Waiting, Failure(error), Unknown, Cancelled
        PollingResult.Waiting
    }
    success { value -> value.isComplete }

    // Retry predicate receives a domain Error? for error cases (see RetryPredicates)
    retry { err ->
        // Example: retry for network/server/timeout/unknown
        RetryPredicates.networkOrServerOrTimeout(err)
    }

    // Observability hooks
    onAttempt { attempt, delayMs -> println("Attempt #$attempt, next delay=$delayMs ms") }
    onResult { attempt, result -> println("Result at $attempt: $result") }
    onComplete { attempts, durationMs, outcome -> println("Done in $attempts attempts: $outcome") }

    backoff(
        BackoffPolicy(
            initialDelayMs = 500,
            maxDelayMs = 5000,
            multiplier = 1.8,
            jitterRatio = 0.15,
            maxAttempts = 12,
            overallTimeoutMs = 30_000,
            perAttemptTimeoutMs = null,
        )
    )
}
```

### RetryPredicates

Built-ins to reduce boilerplate:

```kotlin
retry(RetryPredicates.networkOrServerOrTimeout)
// or
retry(RetryPredicates.always)
// or
retry(RetryPredicates.never)
```

## Sample app integration

The Compose Multiplatform sample uses the Polling facade:

- Start: Polling.startPolling(config) { outcome -> ... }
- Pause/Resume: Polling.pause(id), Polling.resume(id)
- Cancel: Polling.cancel(handle)
- Update backoff: Polling.updateBackoff(id, policy)

See composeApp/src/commonMain/.../App.kt for a full example with UI and logs.

## Migration notes (rename)

- PollingEngineApi has been renamed to PollingApi.
- A dedicated facade object Polling now implements PollingApi and should be used by apps.
- Internal details remain in PollingEngine; apps should avoid direct usage and prefer Polling.

## Reference docs

- Generate Dokka: `./gradlew :pollingengine:dokkaHtml`
- Output: pollingengine/build/dokka/html/index.html

