This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### iOS Simulator/CoreSimulator Troubleshooting

If you see errors like:

- "CoreSimulator.framework was changed while the process was running. Service version (1010.15) does not match expected service version (947.17)."
- "Connection refused"

This indicates your active Xcode's CoreSimulator service doesn't match the running Simulator/runtime. This can happen if Xcode auto-updated in the background or you have multiple Xcode versions installed.

Quick fix (recommended):

- Run the helper script which resets CoreSimulator and clears caches.
  ```bash
  chmod +x scripts/fix-ios-simulator.sh
  # optionally pass an Xcode path to switch the active Xcode
  ./scripts/fix-ios-simulator.sh                     # just reset
  # sudo ./scripts/fix-ios-simulator.sh /Applications/Xcode.app
  ```
- Reopen Xcode and Simulator, then re-run the app.

Manual steps (if you prefer to do it yourself):

1) Ensure the right Xcode is active:
   ```bash
   xcode-select -p
   sudo xcode-select -s /Applications/Xcode.app
   ```
2) Close Simulator and kill CoreSimulator services:
   ```bash
   killall -9 Simulator || true
   killall -9 com.apple.CoreSimulator.CoreSimulatorService || true
   launchctl remove com.apple.CoreSimulator.CoreSimulatorService || true
   ```
3) Shutdown and erase simulators (warning: removes simulator data):
   ```bash
   xcrun simctl shutdown all || true
   xcrun simctl erase all || true
   ```
4) Clear Xcode DerivedData:
   ```bash
   rm -rf ~/Library/Developer/Xcode/DerivedData
   ```
5) Reopen Xcode/Simulator and try again.

Notes:
- Ensure your Simulator runtime version matches your active Xcode (xcrun simctl list runtimes).
- Our iOS deployment target is currently set to 18.2 in the Xcode project; use an iOS 18.x simulator (e.g., 18.6).
- If the issue recurs, run the script again after Xcode updates.

---

### Observability and hooks (PollingEngine)

PollingEngine provides optional observability without forcing any logging dependency:

- onAttempt(attempt, delayMs): called just before each fetch attempt is executed. delayMs is 0 on the first attempt; subsequent attempts include the planned sleep before the attempt.
- onResult(attempt, result): called after each attempt with the PollingResult produced by fetch.
- onComplete(attempts, durationMs, outcome): called once when polling reaches a terminal outcome (Success, Exhausted, Timeout, or Cancelled).

Threading and timing:
- All hooks run on the dispatcher configured in PollingConfig (default: Dispatchers.Default).
- onAttempt and Metrics.recordAttempt are invoked immediately before calling fetch (or inside withTimeout if perAttemptTimeoutMs is set).
- onResult is invoked immediately after fetch completes or fails and has been mapped to a PollingResult.
- onComplete is invoked right after the terminal outcome is determined.

Optional interfaces you can implement:
- Logger: a simple logging callback interface you can pass via PollingConfig.logger (no logging library required).
- Metrics: callbacks to record attempts, results, and completion; used by PollingEngine if provided.

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

---

## Installation

Currently, the library is consumed as a project dependency inside this repo. Once published to Maven Central, use:

- Gradle (Kotlin DSL):
  ```kotlin
  repositories { mavenCentral() }
  dependencies { implementation("in.androidplay:pollingengine:0.1.0") }
  ```
- Maven:
  ```xml
  <dependency>
    <groupId>in.androidplay</groupId>
    <artifactId>pollingengine</artifactId>
    <version>0.1.0</version>
  </dependency>
  ```

iOS consumption options (to be published):
- CocoaPods (planned):
  ```ruby
  pod 'PollingEngine', '~> 0.1'
  ```
- Swift Package Manager (binary XCFramework, planned): add the Git tag `vX.Y.Z` and use Package.swift provided in release notes.

## Quick start (Kotlin)

```
import in.androidplay.pollingengine.models.PollingResult
import in.androidplay.pollingengine.polling.BackoffPolicies
import in.androidplay.pollingengine.polling.PollingConfigBuilder
import in.androidplay.pollingengine.polling.PollingEngine

// Your fetcher should return PollingResult<T>
suspend fun fetchStatus(): PollingResult<String> = TODO()

val config = PollingConfigBuilder<String>()
    .fetch { fetchStatus() }
    .success { value -> value == "READY" }
    .retry { error -> true } // customize as needed
    .backoff(BackoffPolicies.quick20s)
    .onAttempt { attempt, delay -> println("Attempt #$attempt (delay=$delay ms)") }
    .onResult { attempt, result -> println("Result@$attempt = $result") }
    .onComplete { attempts, duration, outcome -> println("Done in $attempts attempts after ${duration}ms: $outcome") }
    .build()

// Use pollUntil in a coroutine
val outcome = PollingEngine.pollUntil(config)
```

## Semantic Versioning

This project follows Semantic Versioning (MAJOR.MINOR.PATCH):
- MAJOR: incompatible API changes
- MINOR: backwards-compatible functionality
- PATCH: backwards-compatible bug fixes

Public API changes are guarded using Kotlin Binary Compatibility Validator. API checks run in CI when a baseline is present.

---

## CI and Release Setup

For step-by-step instructions on configuring CI and publishing the libraries (Maven Central and CocoaPods), see:
- docs/ci-setup.md
