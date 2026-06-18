# PollingEngine

Last updated: 2026-06-19

[![Maven Central](https://img.shields.io/maven-central/v/in.androidplay/pollingengine.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/in.androidplay/pollingengine)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue?logo=kotlin)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-green.svg)](#license)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-inactive.svg)](#setupbuild-instructions)

A Kotlin Multiplatform library for Android and iOS that provides a production‑ready polling engine
behind a **fluent API that reads like a sentence**:

```kotlin
// "Poll the status until it's COMPLETED, every 2 seconds."
Polling.poll { api.checkStatus() }
    .until { it == "COMPLETED" }
    .every(2.seconds)
    .start(scope)
```

You describe the poll, then end with a verb that picks how it runs. Features:

- Exponential backoff and jitter (`.backoff { … }`) or a fixed cadence (`.every(2.seconds)`)
- Timeouts (`.timeout`, `.timeoutPerAttempt`) and attempt caps (`.atMost`), bounded **or unbounded**
- Four run models chosen by the terminal verb: **converge** (`.start`/`.await`),
  **continuous stream** (`.collect`/`.asFlow`), and **multiplexed shared sessions** (`.shared`)
- A live `PollHandle` from `.start()` for `pause` / `resume` / `cancel` / `retune` — no id lookups
- Observability hooks (`.onAttempt` / `.onResult` / `.onComplete`) and domain‑level results

```mermaid
flowchart TD
    A[Start] --> B{Attempt fetch}
    B -->|Success & meets success predicate| C[Outcome: Success]
    B -->|stopWhen predicate true| I[Stop: Exhausted / stream completes]
    B -->|Failure & retryable| D[Backoff delay]
    B -->|Failure & not retryable| E[Outcome: Exhausted]
    B -->|Timeout reached| F[Outcome: Timeout]
    D --> G{More attempts left?}
    G -->|Yes| B
    G -->|No / unlimited| E
    %% External control
    B -. pause/resume/cancel/retune .-> H[PollHandle]
```

Modules:

- [/pollingengine](./pollingengine) — library code
- [/composeApp](./composeApp/src) — sample shared UI (Compose Multiplatform)
- [/iosApp](./iosApp/iosApp) — iOS app entry (SwiftUI)

## Table of Contents

- [Project Overview](#project-overview)
- [The Fluent API in 60 seconds](#the-fluent-api-in-60-seconds)
- [Core Concepts](#core-concepts)
- [Public API Reference](#public-api-reference)
- [Installation and Dependency](#installation-and-dependency)
- [Android Implementation](#android-implementation)
- [Migration from 0.2.x](#migration-from-02x)
- [iOS (Swift) Implementation](#ios-swift-implementation)
- [Backoff & Retry Reference](#backoff--retry-reference)
- [Setup/Build Instructions](#setupbuild-instructions)
- [Publishing & Versioning](#publishing--versioning)
- [Contributing](#contributing)
- [License](#license)

## Project Overview

PollingEngine helps you repeatedly call a function until a condition is met or limits are reached,
or
to continuously observe a remote value over time. It is designed for long‑polling workflows like
waiting for a server job to complete, checking payment/compliance status, or streaming a live list.

Platforms: Kotlin Multiplatform (common code) with Android and iOS targets.

> **API entry point:** everything starts at the `Polling` facade object — call `Polling.poll { … }`
> (or `Polling.pollResult { … }`) and chain from there. The internal engine and config types are
> not part of the public surface.

## The Fluent API in 60 seconds

Every poll is built the same way: **`Polling.poll { fetch }`**, optional refinements, then a
**terminal verb** that decides what you get back.

```kotlin
import `in`.androidplay.pollingengine.polling.Polling
import `in`.androidplay.pollingengine.polling.dsl.Retry
import kotlin.time.Duration.Companion.seconds

// Converge → a controllable handle
val handle = Polling.poll { api.checkStatus() }   // suspend () -> T ; just throw on error
    .until { it == "COMPLETED" }                  // terminal success condition
    .every(2.seconds)                             // cadence (or .backoff { … } for exponential)
    .retryWhen(Retry.networkOrServer)             // optional
    .start(scope)                                 // ← verb: launch + return PollHandle

handle.pause(); handle.resume(); handle.cancel()  // control, no id lookups
```

| Terminal verb | You get | Use it for |
|---|---|---|
| `.start(scope)` | `PollHandle<T>` (control + `outcomes` flow) | fire‑and‑control a converging poll |
| `.await()` | `PollingOutcome<T>` (suspends) | a one‑shot poll inside a coroutine |
| `.collect(scope) { v -> }` | `PollHandle<T>` | react to **every** successful value |
| `.asFlow()` | `Flow<T>` | a cold stream for Compose `collectAsState` |
| `.shared(key)` | `SharedPoll<T>` | one loop fanned out to many subscribers |

Refinements (all optional, sane defaults): `.until` · `.stopWhen` · `.every` · `.backoff { }` ·
`.atMost` · `.timeout` · `.timeoutPerAttempt` · `.retryWhen` · `.mapErrors` · `.on(dispatcher)` ·
`.onAttempt` · `.onResult` · `.onComplete` · `.keepAliveFor` · `.replayLast`.

> Coming from 0.2.x? See the [Migration from 0.2.x](#migration-from-02x) map below.

## Core Concepts

**Your `fetch`** — the most common form is `Polling.poll { … }`, where the lambda returns a plain
value (`T`) and **throws** on error; the engine wraps it and runs throwables through your retry
policy. If you need to say "no value yet, keep polling" without a value, use
`Polling.pollResult { … }` and return a `PollingResult<T>`:

| `PollingResult` variant       | Meaning                                                            |
|-------------------------------|-------------------------------------------------------------------|
| `PollingResult.Success(data)` | A value was retrieved; checked against `.until` / `.stopWhen`.    |
| `PollingResult.Failure(error)`| Failed this tick; retried if `.retryWhen(error)` is true.         |
| `PollingResult.Waiting`       | Not ready yet; keep polling.                                      |
| `PollingResult.Cancelled` / `PollingResult.Unknown` | Cancelled / indeterminate state.            |

**`PollingOutcome<T>`** — the terminal result of a *converge* run (`.start` / `.await` /
`Polling.sequence`):

| Variant                    | Fields                                                            |
|----------------------------|-------------------------------------------------------------------|
| `PollingOutcome.Success`   | `value`, `attempts`, `elapsedMs`                                  |
| `PollingOutcome.Exhausted` | `last`, `attempts`, `elapsedMs` (also used when `.stopWhen` fires)|
| `PollingOutcome.Timeout`   | `last`, `attempts`, `elapsedMs`                                   |
| `PollingOutcome.Cancelled` | `attempts`, `elapsedMs`                                           |

**Run models** (chosen by the terminal verb):

- **Converge** — `.start(scope)` returns a `PollHandle` whose `outcomes` flow emits one
  `PollingOutcome`; `.await()` suspends and returns it directly. Polls until terminal success,
  exhaustion, timeout, or cancellation.
- **Observe** — `.collect(scope) { value -> }` (callback) or `.asFlow()` (cold `Flow<T>`) emit the
  value of *every* successful tick and keep running until a stop condition (`.until`, `.stopWhen`,
  a non‑retryable failure, or limits).
- **Shared** — `.shared(key)` returns a `SharedPoll<T>`: one loop per key fans each tick value out
  to all `stream()` subscribers with a single fetch per tick.

## Public API Reference

The `Polling` facade:

| Member         | Signature                                          | Notes                                  |
|----------------|----------------------------------------------------|----------------------------------------|
| `poll`         | `{ suspend () -> T } -> PollBuilder<T>`            | Start a poll; fetch returns a value.   |
| `pollResult`   | `{ suspend () -> PollingResult<T> } -> PollBuilder<T>` | Advanced: full result vocabulary.  |
| `sequence`     | `suspend (vararg PollBuilder<T>) -> PollingOutcome<T>` | Run polls in order, stop at first non‑success. |
| `cancelAll`    | `suspend ()`                                        | Cancel every active poll.              |
| `shutdown`     | `suspend ()`                                        | Stop the engine; no new polls after.   |
| `activeCount`  | `Int`                                               | Number of active polls (diagnostics).  |
| `activeIds`    | `suspend () -> List<String>`                        | Ids of active polls (diagnostics).     |

`PollBuilder<T>` — refinements `.until` · `.stopWhen` · `.stopWhenResult` · `.every` · `.backoff { }`
· `.atMost` · `.timeout` · `.timeoutPerAttempt` · `.retryWhen` · `.mapErrors` · `.on` · `.onAttempt`
· `.onResult` · `.onComplete` · `.keepAliveFor` · `.replayLast`; terminal verbs `.start(scope)` ·
`.await()` · `.collect(scope) { }` · `.asFlow()` · `.shared(key)`.

`PollHandle<T>`: `val id`, `val outcomes: Flow<PollingOutcome<T>>`, `val isActive`, `val isPaused`,
`suspend pause()/resume()/cancel()`, `retune { … }`.

`SharedPoll<T>`: `val key`, `fun stream(): Flow<T>`, `fun stream(filter: (T) -> Boolean): Flow<T>`.

`Retry` presets (for `.retryWhen`): `Retry.always`, `Retry.never`, `Retry.networkOrServer`.

## Installation and Dependency

Coordinates on Maven Central:

- groupId: `in.androidplay`
- artifactId: `pollingengine`
- version: `1.0.0`

Gradle Kotlin DSL (Android/shared):

```kotlin
repositories { mavenCentral() }
dependencies { implementation("in.androidplay:pollingengine:1.0.0") }
```

Gradle Groovy DSL:

```groovy
repositories { mavenCentral() }
dependencies { implementation "in.androidplay:pollingengine:1.0.0" }
```

Maven:

```xml
<dependency>
  <groupId>in.androidplay</groupId>
  <artifactId>pollingengine</artifactId>
  <version>1.0.0</version>
</dependency>
```

### iOS integration

The iOS framework is published with baseName **`PollingEngine`** (import `PollingEngine` in Swift).

- CocoaPods (from this repository during development):

```ruby
# Podfile (example)
platform :ios, '14.0'
use_frameworks!

target 'YourApp' do
  pod 'pollingengine', :path => '../pollingengine'
end
```

Then:

```bash
./gradlew :pollingengine:generateDummyFramework
cd iosApp && pod install
```

- Swift Package Manager: If you publish an XCFramework, add the package URL and version in Xcode.
  (SPM publication is not configured in this repo out‑of‑the‑box.)

## Android Implementation

On Android you typically drive polling from a `ViewModel` and expose state via `StateFlow`. Three
patterns cover most needs.

### 1. Converge: poll until a job completes

Describe the poll and end with `.start(viewModelScope)`. You get a `PollHandle` back immediately —
collect its `outcomes` for the result, and use it directly to pause/resume/cancel.

```kotlin
import `in`.androidplay.pollingengine.polling.Polling
import `in`.androidplay.pollingengine.polling.PollingOutcome
import `in`.androidplay.pollingengine.polling.dsl.PollHandle
import `in`.androidplay.pollingengine.polling.dsl.Retry
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

class JobStatusViewModel(
    private val api: JobApi,
    private val jobId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JobUiState>(JobUiState.Idle)
    val uiState: StateFlow<JobUiState> = _uiState.asStateFlow()

    private var handle: PollHandle<String>? = null

    fun startPolling() {
        if (handle != null) return
        _uiState.value = JobUiState.Loading

        val poll = Polling.poll { api.checkStatus(jobId) }   // just throw on error
            .until { it.equals("COMPLETED", ignoreCase = true) }
            .retryWhen(Retry.networkOrServer)
            .backoff {
                initialDelay = 1.seconds
                maxDelay = 10.seconds
                multiplier = 1.5
            }
            .atMost(10)
            .timeout(2.minutes)
            .onAttempt { attempt, delayMs -> Log.d("Poll", "attempt #$attempt, next in $delayMs ms") }
            .start(viewModelScope)
        handle = poll

        viewModelScope.launch {
            poll.outcomes.collect { outcome ->
                _uiState.value = when (outcome) {
                    is PollingOutcome.Success   -> JobUiState.Success(outcome.value)
                    is PollingOutcome.Exhausted -> JobUiState.Error("Exhausted after ${outcome.attempts} attempts")
                    is PollingOutcome.Timeout   -> JobUiState.Error("Timed out after ${outcome.elapsedMs} ms")
                    is PollingOutcome.Cancelled -> JobUiState.Idle
                }
                handle = null
            }
        }
    }

    fun pause() = viewModelScope.launch { handle?.pause() }
    fun resume() = viewModelScope.launch { handle?.resume() }

    fun cancelPolling() {
        viewModelScope.launch { handle?.cancel() }
        handle = null
    }
}

sealed interface JobUiState {
    data object Idle : JobUiState
    data object Loading : JobUiState
    data class Success(val data: String) : JobUiState
    data class Error(val message: String) : JobUiState
}
```

For a one‑shot poll inside a coroutine, drop the handle entirely and use `.await()`:

```kotlin
val outcome = Polling.poll { api.checkStatus(jobId) }
    .until { it == "COMPLETED" }
    .every(2.seconds)
    .await()
```

### 2. Observe: a continuous live stream

`.collect(scope) { }` reacts to every successful tick and stops when `.stopWhen` (or a
terminal/limit) fires. Pair it with `.every(…)` for a steady cadence.

```kotlin
Polling.poll { api.currentQueuePosition() }
    .every(2.seconds)               // one tick / 2s, forever
    .stopWhen { it == 0 }           // stop when we reach the front
    .collect(viewModelScope) { position ->
        _uiState.update { it.copy(queuePosition = position) }
    }
```

For Compose, `.asFlow()` gives a cold `Flow<T>` you can `collectAsState`:

```kotlin
val position by remember {
    Polling.poll { api.currentQueuePosition() }.every(2.seconds).asFlow()
}.collectAsState(initial = null)
```

### 3. Shared: one network call, many subscribers

`.shared(key)` de‑duplicates by `key`: a single fetch per tick is fanned out to every `stream()`
collector. Polling starts on the first subscriber and stops `keepAliveFor` after the last leaves.

```kotlin
val session = Polling.poll { repository.getServicesList(vin) }
    .every(10.seconds)              // one tick / 10s, forever
    .stopWhen { it.isEmpty() }      // stop when the list drains
    .keepAliveFor(15.seconds)       // keep alive 15s after last subscriber leaves
    .replayLast(1)                  // late subscribers get the last value
    .shared(key = vin)

// Two independent views fed by the SAME 10s network call:
viewModelScope.launch {
    session.stream { services -> services.any { it.isActive } }
        .collect { active -> _activeState.value = active }
}
viewModelScope.launch {
    session.stream { services -> services.any { it.needsAssociation } }
        .collect { assoc -> _assocState.value = assoc }
}
```

### Control: the handle, not ids

`.start()` / `.collect()` return a `PollHandle` — control the poll directly, no id lookups:

```kotlin
handle.pause()
handle.resume()
handle.retune { initialDelay = 5.seconds; maxDelay = 5.seconds }  // hot‑swap backoff
handle.cancel()
// Polling.cancelAll() / Polling.shutdown() for global control
```

> **Lifecycle:** the poll stops automatically when the `scope` you pass to `.start()`/`.collect()`
> is cancelled (e.g. `viewModelScope`), so leaks are avoided without manual teardown.

## Migration from 0.2.x

The builder/`PollingConfig` API is gone; everything now flows from `Polling.poll { … }`.

| 0.2.x | 1.0.0 |
|---|---|
| `Polling.startPolling { fetch=…; isTerminalSuccess=…; backoff=BackoffPolicy(…) }.launchIn(scope)` | `Polling.poll { … }.until { … }.backoff { … }.start(scope)` |
| `Polling.run(config)` | `Polling.poll { … }.until { … }.await()` |
| `Polling.observe { … }.collect { … }` | `Polling.poll { … }.every(d).collect(scope) { … }` (or `.asFlow()`) |
| `Polling.shared(key) { …; stopTimeoutMs=…; replay=… }` | `Polling.poll { … }.every(d).keepAliveFor(d).replayLast(n).shared(key)` |
| `Polling.compose(a, b)` | `Polling.sequence(a, b)` |
| `fetch = { PollingResult.Success(api()) }` | `Polling.poll { api() }` (plain value; throw on error) |
| `isTerminalSuccess = { … }` | `.until { … }` |
| `shouldRetryOnError = RetryPredicates.networkOrServerOrTimeout` | `.retryWhen(Retry.networkOrServer)` |
| `backoff = BackoffPolicies.fixed(2_000)` | `.every(2.seconds)` |
| `backoff = BackoffPolicy(initialDelayMs=…, …)` | `.backoff { initialDelay = …; … }` / `.atMost` / `.timeout` |
| `listActiveIds()` + `pause(id)`/`updateBackoff(id,…)` | `val h = ….start(scope); h.pause()` / `h.retune { … }` |
| `SharedPollingSession<T>` | `SharedPoll<T>` |

## iOS (Swift) Implementation

Expose Kotlin Flows to Swift through a thin helper in your shared module, then bind to SwiftUI.
Import the framework as **`PollingEngine`**.

### Shared Kotlin helper

```kotlin
// shared module, e.g. IosPollingHelper.kt
import `in`.androidplay.pollingengine.polling.Polling
import `in`.androidplay.pollingengine.polling.PollingOutcome
import `in`.androidplay.pollingengine.polling.dsl.PollHandle
import `in`.androidplay.pollingengine.polling.dsl.Retry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

object IosPollingHelper {

    private val scope = CoroutineScope(Dispatchers.Main)

    /** Converge: drives a job to completion and reports a single outcome. */
    fun startStatusPolling(
        fetch: suspend () -> String,
        onUpdate: (Int) -> Unit,
        onComplete: (PollingOutcome<String>) -> Unit,
    ): PollHandle<String> = Polling.poll { fetch() }
        .until { it.equals("COMPLETED", ignoreCase = true) }
        .every(2.seconds)
        .retryWhen(Retry.networkOrServer)
        .onAttempt { attempt, _ -> onUpdate(attempt) }
        .start(scope)
        .also { handle -> handle.outcomes.onEach { onComplete(it) }.launchIn(scope) }

    /** Observe: a continuous stream of every successful value. */
    fun observeQueue(
        fetch: suspend () -> Int,
        onValue: (Int) -> Unit,
    ): PollHandle<Int> = Polling.poll { fetch() }
        .every(2.seconds)
        .stopWhen { it == 0 }
        .collect(scope) { onValue(it) }
}
```

> The helper returns a `PollHandle` — call `handle.cancel()` from Swift to stop the poll (no `Job`
> bookkeeping or id lookups needed).

### SwiftUI ViewModel

```swift
import SwiftUI
import PollingEngine // KMP framework baseName

@MainActor
final class PollingViewModel: ObservableObject {
    @Published var status: String = "Idle"
    private var poll: PollHandle?

    func start() {
        status = "Polling…"
        poll = IosPollingHelper.shared.startStatusPolling(
            fetch: { try await MyApi.shared.checkStatus() },
            onUpdate: { [weak self] attempt in self?.status = "Attempt \(attempt)" },
            onComplete: { [weak self] outcome in
                switch outcome {
                case let success as PollingOutcome.Success<NSString>:
                    self?.status = "Success: \(success.value)"
                case let exhausted as PollingOutcome.Exhausted:
                    self?.status = "Exhausted after \(exhausted.attempts) attempts"
                case is PollingOutcome.Timeout:
                    self?.status = "Timed out"
                case is PollingOutcome.Cancelled:
                    self?.status = "Cancelled"
                default:
                    break
                }
            }
        )
    }

    func cancel() {
        poll?.cancel(completionHandler: { _, _ in })
        poll = nil
        status = "Idle"
    }
}
```

### SwiftUI View

```swift
struct ContentView: View {
    @StateObject private var viewModel = PollingViewModel()

    var body: some View {
        VStack(spacing: 16) {
            Text(viewModel.status)
            Button("Start Polling") { viewModel.start() }
            Button("Cancel") { viewModel.cancel() }
        }
        .padding()
    }
}
```

> **Tip:** `.shared(key)`, `.await()`, and `PollHandle.pause/resume/cancel` are `suspend` members
> exposed to Swift as completion‑handler / `async` functions. Wrap them in helper functions in the
> shared module (as above) to keep call sites clean, and collect Flows there rather than in Swift.

## Backoff & Retry Reference

**Cadence.** Pick one:

- `.every(2.seconds)` — constant cadence (no growth, no jitter); unbounded by default — the natural
  fit for observe / shared.
- `.backoff { … }` — exponential with jitter. All knobs are `Duration`/`Double` with safe defaults:

```kotlin
.backoff {
    initialDelay = 500.milliseconds   // delay before the 2nd attempt; grows by multiplier
    maxDelay = 30.seconds             // ceiling the delay is clamped to
    multiplier = 2.0                  // growth factor each round (>= 1.0)
    jitter = 0.2                      // [0.0, 1.0]; 0 disables jitter
    maxAttempts = 8                   // null = unlimited
    overallTimeout = 120.seconds      // null = no overall timeout
    perAttemptTimeout = null          // null disables; must be > 0 when set
}
```

**Limits** (override the cadence's caps, read more clearly):

- `.atMost(10)` — cap the number of attempts.
- `.timeout(2.minutes)` — cap the overall wall‑clock time.
- `.timeoutPerAttempt(10.seconds)` — cap each attempt (a slower fetch becomes a retryable timeout).

Validation rejects negative values, `maxDelay < initialDelay`, and `multiplier < 1.0`.

**Retry** (`.retryWhen(…)`, with `Retry` presets):

- `Retry.networkOrServer` — retry network/server/timeout/unknown errors (recommended default).
- `Retry.always` / `Retry.never`.

**Error mapping** (`.mapErrors { throwable -> Error }`) translates a thrown exception into a domain
`Error` for `.retryWhen`. The default maps any throwable to `Error(-1, message)`, which
`Retry.networkOrServer` treats as retryable.

## Setup/Build Instructions

Clone and build:

```bash
git clone https://github.com/bosankus/PollingEngine.git
cd PollingEngine
./gradlew build
```

Run tests (all targets where applicable):

```bash
./gradlew :pollingengine:allTests
```

Android app:

```bash
./gradlew :composeApp:installDebug
```

iOS builds (macOS):

- Open `iosApp` in Xcode and run on a simulator.
- If CoreSimulator issues arise, run `scripts/fix-ios-simulator.sh` then retry.

## Publishing & Versioning

Publishing to Maven Central uses `com.vanniktech.maven.publish`.

- Required environment variables/Gradle properties (typically set in CI):
    - `OSSRH_USERNAME`, `OSSRH_PASSWORD`
    - `SIGNING_KEY` (Base64 GPG private key), `SIGNING_PASSWORD`
    - `GROUP`: `in.androidplay` (already configured)
- Commands:

```bash
./gradlew :pollingengine:publishToMavenLocal
./gradlew :pollingengine:publish --no-configuration-cache
```

Versioning policy: Semantic Versioning (MAJOR.MINOR.PATCH). Public API stability is guarded by
Kotlin Binary Compatibility Validator; run `./gradlew apiCheck` when modifying public APIs.

Release notes: maintain [CHANGELOG.md](CHANGELOG.md) for each version. Tag releases on Git and
reference them in release notes.

## Contributing

We welcome contributions!

- Fork the repo and create a feature branch
- Follow Kotlin style and ktlint; run `./gradlew ktlintCheck detekt`
- Ensure tests pass: `./gradlew build`
- Open a Pull Request describing your changes

Guidelines and policies:

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- Binary compatibility: explicit API mode and API checks are enabled; please run
  `./gradlew apiCheck`
  when modifying public APIs.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Copyright (c) 2025 AndroidPlay

## Maintainers & Support

- Maintainer: @bosankus
- Issues: use [GitHub Issues](https://github.com/bosankus/PollingEngine/issues)
- Security: see [SECURITY.md](SECURITY.md)
  </content>
  </invoke>
