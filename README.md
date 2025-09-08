# PollingEngine

Last updated: 2025-09-08 17:36

[![Maven Central](https://img.shields.io/maven-central/v/in.androidplay/pollingengine.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/in.androidplay/pollingengine)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue?logo=kotlin)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-green.svg)](#license)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-inactive.svg)](#setupbuild-instructions)

A Kotlin Multiplatform library for Android and iOS that provides a production‑ready polling engine
with:

- Exponential backoff and jitter
- Timeouts (overall and per‑attempt)
- Cancellation and control APIs
- Observability hooks (attempt/result/complete)

Mermaid flow diagram (GitHub renders this):

```mermaid
flowchart TD
    A[Start] --> B{Attempt fetch}
    B -->|Success & meets success predicate| C[Outcome: Success]
    B -->|Failure & retryable| D[Backoff delay]
    B -->|Failure & not retryable| E[Outcome: Exhausted]
    B -->|Timeout reached| F[Outcome: Timeout]
    D --> G{More attempts left?}
    G -->|Yes| B
    G -->|No| E
    %% External control
    B -. pause/resume/cancel .-> H[Control APIs]
```

- Modules:
    - [/pollingengine](./pollingengine) — library code
    - [/composeApp](./composeApp/src) — sample shared UI (Compose Multiplatform)
    - [/iosApp](./iosApp/iosApp) — iOS app entry (SwiftUI)

## Project Overview

Note: Public API rename — PollingEngineApi has been renamed to PollingApi, and apps should use the
facade object `Polling` instead of referencing `PollingEngine` directly.

PollingEngine helps you repeatedly call a function until a condition is met or limits are reached.
It is designed for long‑polling workflows like waiting for a server job to complete, checking
payment status, etc.

Platforms: Kotlin Multiplatform (common code) with Android and iOS targets.

Highlights:

- Simple DSL with pollingConfig { … }
- Backoff presets (e.g., BackoffPolicies.quick20s)
- Control operations: pause(id), resume(id), cancel(id), cancelAll(), shutdown()
- Domain‑level results via PollingResult and terminal PollingOutcome

## Installation and Dependency

Coordinates on Maven Central:

- groupId: in.androidplay
- artifactId: pollingengine
- version: 0.1.1

Gradle Kotlin DSL (Android/shared):

```kotlin
repositories { mavenCentral() }
dependencies { implementation("in.androidplay:pollingengine:0.1.1") }
```

Gradle Groovy DSL:

```groovy
repositories { mavenCentral() }
dependencies { implementation "in.androidplay:pollingengine:0.1.1" }
```

Maven:

```xml

<dependency>
  <groupId>in.androidplay</groupId>
    <artifactId>pollingengine</artifactId>
  <version>0.1.1</version>
</dependency>
```

iOS integration options:

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

- Swift Package Manager: If you publish an XCFramework, add the package URL and version in Xcode. (
  SPM publication is not configured in this repo out‑of‑the‑box.)

## Usage

Basic shared usage:

```kotlin

val config = pollingConfig<String> {
    fetch { /* return PollingResult<String> */ TODO() }
    success { it == "READY" }
    // Retry for common transient errors (network/server/timeout/unknown)
    retry(RetryPredicates.networkOrServerOrTimeout)
    backoff(BackoffPolicies.quick20s)
}

suspend fun run(): PollingOutcome<String> = Polling.run(config)
```

Android example (ViewModel + Compose):

```kotlin
class StatusViewModel : ViewModel() {
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val config = pollingConfig<String> {
        fetch { TODO("Return PollingResult<String>") }
        success { it == "READY" }
        backoff(BackoffPolicies.quick20s)
    }

    fun runOnce() = viewModelScope.launch {
        _status.value = Polling.run(config).toString()
    }
}
```

iOS example (Swift calling Kotlin helper):

```kotlin
// shared Kotlin
object IosAdapters {
    fun provideStatusConfig(): PollingConfig<String> = pollingConfig {
        fetch { TODO() }
        success { it == "READY" }
        backoff(BackoffPolicies.quick20s)
    }
}
```

```swift
// Swift

import PollingEngine

let handle = InAndroidplayPollingengineAdaptersIosAdapters().startStatusPolling { outcome in
    print("Outcome: \(outcome)")
}
```

API Reference:

- Generate locally with Dokka: `./gradlew :pollingengine:dokkaHtml`
- Output is in `pollingengine/build/dokka/html/index.html`

Platform‑specific notes:

- expect/actual: Core engine lives in commonMain. If you introduce platform APIs, add expect
  declarations in common and provide actual implementations in androidMain/iosMain.
- Coroutines: library uses kotlinx.coroutines; ensure proper dispatchers on each platform.

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

- Open iosApp in Xcode and run on a simulator.
- If CoreSimulator issues arise, run `scripts/fix-ios-simulator.sh` then retry.

## Publishing & Versioning

Publishing to Maven Central uses com.vanniktech.maven.publish.

- Required environment variables/Gradle properties (typically set in CI):
    - OSSRH_USERNAME, OSSRH_PASSWORD
    - SIGNING_KEY (Base64 GPG private key), SIGNING_PASSWORD
  - GROUP: in.androidplay (already configured)
- Commands:

```bash
./gradlew :pollingengine:publishToMavenLocal
./gradlew :pollingengine:publish --no-configuration-cache
```

- See docs/ci-setup.md and docs/tasks.md for more details and suggested CI steps.

Versioning policy: Semantic Versioning (MAJOR.MINOR.PATCH). Public API stability is guarded by
Kotlin Binary Compatibility Validator.

Release notes: maintain CHANGELOG.md for each version. Tag releases on Git and reference them in
release notes.

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
  `./gradlew apiCheck` when modifying public APIs.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Copyright (c) 2025 AndroidPlay

## Maintainers & Support

- Maintainer: @bosankus
- Issues: use [GitHub Issues](https://github.com/bosankus/PollingEngine/issues)
- Security: see [SECURITY.md](SECURITY.md)

## Control APIs and Runtime Updates

Start polling by collecting the returned Flow, and control active sessions by ID:

```kotlin
// Start and collect in your scope
val flow = Polling.startPolling(config)
val job = flow.onEach { outcome ->
    println("Outcome: $outcome")
}.launchIn(scope)

// Introspection
val ids = Polling.listActiveIds() // suspend; returns List<String>
println("Active: $ids (count=${Polling.activePollsCount()})")

// Pause/resume first active session (example)
if (ids.isNotEmpty()) {
  val id = ids.first()
  Polling.pause(id)
  // ... later
  Polling.resume(id)

  // Update backoff at runtime
  Polling.updateBackoff(id, BackoffPolicies.quick20s)

  // Cancel
  Polling.cancel(id)
}

// Or cancel all
Polling.cancelAll()

// Stop collecting if needed
job.cancel()
```

## RetryPredicates examples

Built-ins to reduce boilerplate:

```kotlin
// Retry for network/server/timeout/unknown errors (recommended)
retry(RetryPredicates.networkOrServerOrTimeout)

// Always retry on failures
retry(RetryPredicates.always)

// Never retry on failures
retry(RetryPredicates.never)
```

## More documentation

- docs/pollingengine.md — Web Guide (overview, install, Android/iOS usage)
- docs/DeveloperGuide.md — Developer Guide (API overview, DSL, migration, reference)
