# Feature Request: Continuous, multiplexed, unbounded polling mode for

`in.androidplay:pollingengine`

## Role

You are working on the `in.androidplay:pollingengine` Kotlin Multiplatform library
(package `in.androidplay.pollingengine`). A consumer needs capabilities the current
API cannot express. Add them in a backward-compatible way — do not break the existing
`Polling.startPolling { }` / `run()` / `compose()` surface or the `PollingOutcome` model.

## Current API (for grounding)

- `Polling.startPolling<T> { ... }: Flow<PollingOutcome<T>>` — converges to a single
  terminal `PollingOutcome` (Success/Exhausted/Timeout/Cancelled), then stops.
- `PollingConfig<T>(fetch, isTerminalSuccess, shouldRetryOnError, backoff, dispatcher,
  onAttempt, onResult, onComplete, throwableMapper)`.
- `BackoffPolicy(initialDelayMs, maxDelayMs, multiplier, jitterRatio, maxAttempts,
  overallTimeoutMs, perAttemptTimeoutMs, random)` with `init` requiring
  `maxAttempts > 0`, `overallTimeoutMs > 0`, `multiplier >= 1.0`.
- `PollingResult<T>` = Success/Failure/Cancelled/Waiting/Unknown.

## The consumer's use case (what they need to model)

An always-on, **indefinite** observer of a remote list (`getServicesList(vin)`), where:

1. Polling runs at a **fixed interval** forever — there is an explicit product
   requirement of "no attempt limit and no overall timeout."
2. Every **successful, non-empty** poll result must be **emitted continuously** to
   downstream consumers — this is a live stream, not a converge-then-stop operation.
3. A **single underlying poll** per key (VIN) must fan out to **multiple independent
   subscribers**, each applying its **own terminal/filter predicate**, while making
   **exactly one network call per tick** regardless of subscriber count.
4. Polling should be **subscriber-driven**: start on first subscriber, and stop a
   configurable grace period after the last subscriber leaves.
5. Polling should **stop on a non-success terminal condition** too (e.g. fetch returns
   an empty list), distinct from "terminal success."

The current API cannot express 1–5 cleanly: `BackoffPolicy` forbids unbounded runs,
`startPolling` is converge-then-stop (single terminal outcome, single predicate), and
there is no built-in multiplexing or fixed-interval scheduler.

## Required features

### F1 — Unbounded polling

Allow `maxAttempts` and `overallTimeoutMs` to express "unlimited" without hacks.

- Accept a sentinel (e.g. `maxAttempts = 0` or a `null`/`UNLIMITED` constant) meaning
  "no limit", and likewise for `overallTimeoutMs`.
- Relax the `init` `require(...)` validations accordingly (keep validation for all
  other values).
- Keep current defaults unchanged so existing callers are unaffected.

### F2 — Fixed-interval scheduling as a first-class option

Provide a clear way to poll at a constant interval (today this only works as a
side effect of `multiplier = 1.0, jitterRatio = 0.0`). Add either:

- a `FixedIntervalPolicy(intervalMs, perAttemptTimeoutMs?, maxAttempts?, overallTimeoutMs?)`,
  or
- a `BackoffPolicies.fixed(intervalMs)` preset and document the semantics.

### F3 — Continuous streaming mode

Add an entry point that emits **each** successful poll value, not just a terminal
outcome. Suggested shape:

```kotlin
public fun <T> Polling.observe(
    builder: PollingConfigBuilder<T>.() -> Unit
): Flow<T>            // emits every Success value per tick; never auto-completes unless a stop condition fires
```

- Emits on every `PollingResult.Success`.
- Errors/timeouts per tick are surfaced via existing `onResult`/`onAttempt` callbacks
  and **skipped** (do not terminate the stream) when `shouldRetryOnError` is true.

### F4 — Stop predicate (non-success terminal)

Add an optional `stopWhen: (PollingResult<T>) -> Boolean` (or
`isTerminalStop: (T) -> Boolean`) that ends polling **without** a `Success` outcome —
e.g. stop when the fetched list is empty. Distinct from `isTerminalSuccess`.

### F5 — Multiplexed / shared sessions (the key one)

Provide a way to run **one** polling loop and fan its results out to multiple
subscribers, each with its own filter, making one `fetch()` call per tick:

```kotlin
public fun <T> Polling.shared(
    key: Any,                               // e.g. VIN; reuses the live session for the same key
    builder: PollingConfigBuilder<T>.() -> Unit
): SharedPollingSession<T>

public interface SharedPollingSession<T> {
    public fun stream(): Flow<T>                       // raw per-tick successes
    public fun stream(filter: (T) -> Boolean): Flow<T> // independent filtered view, same upstream poll
}
```

- Same `key` returns the same live session (one network call per tick shared by all).
- A request for a different `key` starts a separate session.

### F6 — Subscriber-driven lifecycle

The shared session must start on first subscriber and stop a configurable grace
period after the last subscriber unsubscribes (conceptually `WhileSubscribed`).

- Add `stopTimeoutMs: Long = 0` (or similar) to the config/builder.
- Provide `replay` configuration (default replay last value to late subscribers).

## Constraints & acceptance criteria

- 100% backward compatible: existing `startPolling`, `run`, `compose`, `PollingConfig`,
  `BackoffPolicy`, and `PollingOutcome` behavior must be unchanged for current callers.
- Multiplatform-safe (commonMain; no JVM/Android-only APIs).
- Add unit tests covering: unbounded run (F1), constant cadence (F2), continuous
  emission over N ticks (F3), stop-on-empty (F4), single fetch per tick shared by 2+
  subscribers with distinct filters (F5), and start/stop on subscriber
  attach/detach with grace period (F6). Use the existing injectable `random` /
  dispatcher patterns so tests are deterministic (virtual time).
- Update KDoc on the new entry points with runnable examples.
- Bump the minor version and add a CHANGELOG entry.

## Reference end-state the consumer wants to write

```kotlin
val session = Polling.shared(key = vin) {
    fetch = { repository.getServicesList(vin).toPollingResult() }
    backoff = BackoffPolicies.fixed(10_000)        // F2
    // F1: unbounded
    stopWhen = { it is PollingResult.Success && it.data.isEmpty() }  // F4
    perAttemptTimeoutMs = 30_000
    stopTimeoutMs = 15_000                          // F6
}

val activations = session.stream { services -> services.any { it.isActive } }   // F5
val associations = session.stream { services -> services.carAssociationId.isNotEmpty() }
// One network call per 10s tick feeds both flows; runs while subscribed, forever.
```
