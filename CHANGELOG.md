# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning (SemVer).

## [Unreleased]
- CI workflows (build, static checks) and CodeQL added.
- Release workflow scaffolding (tag `v*`) with conditional publishing to Sonatype.
- Binary Compatibility Validator plugin applied (baseline pending).
- Documentation updates and community files (LICENSE, CODE_OF_CONDUCT, CONTRIBUTING, SECURITY).

## [1.0.0]

**Breaking:** the public API is replaced by a single fluent surface that reads like a sentence. You
now describe a poll with `Polling.poll { … }` and end with a terminal verb that selects the run
model. The old builder/config types are internalized; existing call sites must migrate (see the
[migration map](README.md#migration-from-02x)).

### Added

- **Fluent builder**: `Polling.poll { fetch }` (plain value; throw on error) /
  `Polling.pollResult { … }` (full `PollingResult`), with chainable refinements `.until`,
  `.stopWhen` / `.stopWhenResult`, `.every(Duration)`, `.backoff { … }`, `.atMost`, `.timeout`,
  `.timeoutPerAttempt`, `.retryWhen`, `.mapErrors`, `.on(dispatcher)`, `.onAttempt`/`.onResult`/
  `.onComplete`, `.keepAliveFor`, `.replayLast`.
- **Terminal verbs** that pick the run model: `.start(scope)` → `PollHandle<T>`, `.await()` →
  `PollingOutcome<T>`, `.collect(scope) { }` → `PollHandle<T>`, `.asFlow()` → `Flow<T>`,
  `.shared(key)` → `SharedPoll<T>`. `Polling.sequence(vararg)` replaces `compose`.
- **`PollHandle<T>`**: a live control surface returned by `.start()`/`.collect()` with `id`,
  `outcomes`, `isActive`/`isPaused`, `pause()`/`resume()`/`cancel()`, and `retune { … }` — the id is
  available synchronously, removing the need to resolve a session id after starting.
- **`Retry`** presets (`always` / `never` / `networkOrServer`) for `.retryWhen`.
- **`Duration`-based timing** throughout (`.every(2.seconds)`, `.timeout(2.minutes)`).

### Changed

- `SharedPollingSession<T>` renamed to `SharedPoll<T>`.
- Engine launch now allocates the poll id eagerly and returns it with the outcomes flow, so handle
  control no longer goes through an id registry.

### Removed

- `Polling.startPolling` / `run` / `observe` / `shared` / `compose`, the `PollingApi` interface,
  `PollingSession`, and the public `PollingConfig` / `PollingConfigBuilder` / `pollingConfig { }`.
- Public `BackoffPolicy` / `BackoffPolicies` / `RetryPredicates` / `ThrowableMappers` (internalized;
  use `.every` / `.backoff { }` / `Retry` / `.mapErrors` instead).
- Id-based control (`pause(id)`/`resume(id)`/`cancel(id)`/`updateBackoff(id)`/`listActiveIds` as a
  control mechanism); use the `PollHandle`. `activeCount`/`activeIds()` remain for diagnostics.

## [0.2.0]

Continuous, multiplexed, unbounded polling. All additions are backward compatible — existing
`startPolling`/`run`/`compose`, `PollingConfig`, `BackoffPolicy`, and `PollingOutcome` behavior is
unchanged for current callers.

### Added

- **Unbounded polling** (F1): `BackoffPolicy.maxAttempts = 0` (`UNLIMITED_ATTEMPTS`) and
  `overallTimeoutMs = 0` (`NO_TIMEOUT`) now mean "no limit"; helper flags `isAttemptsUnlimited` /
  `isOverallTimeoutDisabled`. Defaults are unchanged (8 attempts / 120s).
- **Fixed-interval preset** (F2): `BackoffPolicies.fixed(intervalMs, …)` for constant cadence
  (unbounded by default).
- **Continuous streaming** (F3): `Polling.observe { } : Flow<T>` emits every per-tick `Success`
  value instead of converging to a single outcome.
- **Stop predicate** (F4): `PollingConfig.stopWhen` / builder `stopWhen` ends polling on a
  non-success terminal (e.g. empty list) — `Exhausted` in converge mode, stream completion in
  streaming mode.
- **Multiplexed sessions** (F5): `Polling.shared(key) { } : SharedPollingSession<T>` runs one poll
  loop per key (one `fetch()` per tick) and fans results to multiple subscribers via `stream()` /
  `stream(filter)`.
- **Subscriber-driven lifecycle** (F6): builder `stopTimeoutMs` and `replay` control WhileSubscribed
  start/stop grace and replay to late subscribers.

### Changed

- `BackoffPolicy` validation relaxed to accept the `0` sentinels (`maxAttempts`/`overallTimeoutMs`);
  negative values still rejected.
- **Note:** `Polling.shared` is a `suspend` function (registry access is mutex-guarded for
  multiplatform thread-safety).

## [0.1.0] - 2025-09-05
Initial extraction of PollingEngine as a KMP library.
- Kotlin Multiplatform library module `:pollingengine` with Android and iOS (XCFramework) targets.
- Polling engine with backoff, jitter, hooks, and metrics interfaces.
- Sample `composeApp` depending on the library.
