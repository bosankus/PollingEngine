# PollingEngine Improvement Tasks Checklist

Last updated: 2025-09-05 02:27

Note: The items are ordered to minimize risk: refactors first, quality gates next, then release automation. Sub‑tasks include concrete actions.

## Initial Tasks Checklist

1. [x] Split app and library modules (establish a publishable KMP library)
   - [x] Create a new `:pollingengine` Kotlin Multiplatform library module (use `com.android.library` + `kotlin-multiplatform`).
   - [x] Move core sources from `composeApp/src/commonMain/.../polling` and `.../models` into `pollingengine/src/commonMain`.
   - [x] Keep `composeApp` as sample/demo app depending on `:pollingengine`.
   - [x] Remove sample utilities (e.g., `PollingSamples`) from library; keep in `composeApp`.
   - [x] Set proper package names and update imports in moved files.

2. [ ] Define public API surface and semantic versioning
   - [x] Review which classes/functions are public (PollingEngine, PollingConfig, BackoffPolicy, outcomes).
   - [ ] Add/review `@JvmOverloads`, `@JvmName`, and `@Throws` where interop requires.
   - [x] Establish semantic versioning policy (MAJOR.MINOR.PATCH) and document in README.
   - [x] Introduce `api` vs `internal` visibility (and `internal` for non-API helpers).

3. [x] Decouple external error code dependency
   - [x] Remove coupling to `com.stellantis.space.core.repositories.UNKNOWN_ERROR_CODE`.
   - [x] Define library-internal error codes or a sealed error model in `models`.
   - [x] Provide mapping hooks so consumers can adapt to their error taxonomy.

4. [x] Improve configuration ergonomics
   - [x] Finalize/streamline `PollingConfigBuilder` DSL (inline lambdas with receiver, defaults).
   - [x] Provide convenience factory methods and presets (`BackoffPolicies`).
   - [x] Validate invariants at build time with descriptive messages.

5. [x] Observability hooks and structured logging
   - [x] Keep `onAttempt`, `onResult`, `onComplete`; document their threading and timings.
   - [x] Provide optional logger interface (expect/actual or simple callback) without forcing a logging dependency.
   - [x] Add simple metrics hooks (attempts, durations) through an interface that users can implement.

6. [x] Concurrency and cancellation hardening
   - [x] Add explicit APIs to query active polls and optionally cancel by handle/token.
   - [x] Audit `CancellationException` handling paths; add tests for rogue vs real cancels.
   - [x] Ensure no shared mutable state leaks across polls (e.g., `activePolls` synchronization if needed for concurrency safety across threads/platforms).

7. [x] Performance considerations
   - [x] Verify backoff growth, jitter bounds, and max delay clamping; micro-benchmark if needed.
   - [x] Avoid unnecessary allocations inside polling loop; consider making `Random` source injectable for deterministic tests.

8. [x] Code quality and style gates
   - [x] Add Detekt with a sensible baseline and rules (complexity, error-prone, style).
   - [x] Add ktlint (or Ktlint Gradle) and configure formatting tasks.
   - [x] Add Kotlin Binary Compatibility Validator to guard public API surface.
   - [x] Enable explicit API mode for the library module.

9. [x] Documentation generation
   - [x] Add Dokka to generate API docs for KMP targets.
   - [x] Publish docs to GitHub Pages (or attach to releases) via CI.
   - [x] Update root README with usage, configuration, and platform notes.

10. [ ] Testing strategy and coverage
    - [ ] Replace placeholder test with meaningful unit tests for:
      - [x] BackoffPolicy validation (bounds, errors on invalid input).
      - [x] Jitter range correctness (min/max bounds, distribution sanity).
      - [ ] Polling loop: terminal success, exhausted, timeout, cancelled scenarios.
      - [ ] Per-attempt timeout behavior using test dispatcher and virtual time.
      - [ ] Error handling and retry predicates.
    - [ ] Add common tests runnable on JVM and Native (where viable).
    - [ ] Configure `kotlinx-coroutines-test` for common tests (JVM) and document limits for Native.
    - [ ] Add minimal integration tests in `composeApp` sample (Android instrumented optional).

11. [ ] Build configuration for library publishing (Android + iOS)
   - [x] Set `group`, `version`, `description`, and POM metadata (name, url, licenses, developers, scm) in Gradle.
   - [x] Apply `maven-publish` and `signing` plugins to `:pollingengine`.
   - [x] Configure Android target as library (AAR) with publication variants.
   - [x] Configure iOS targets and an `XCFramework` artifact for release builds.
   - [x] Add root `io.github.gradle-nexus.publish-plugin` for Sonatype.
   - [x] Externalize secrets (OSSRH user/pass, GPG keyId/password) via environment variables or `~/.gradle/gradle.properties`.

12. [ ] Continuous Integration (CI)
    - [x] Add GitHub Actions workflow to build and test on PRs (matrix: macOS for iOS, Ubuntu for JVM/Android).
    - [x] Add static checks (Detekt, ktlint, API check, Dokka) to CI.
    - [x] Cache Gradle and Kotlin/Native to speed up runs.

13. [ ] Release automation (tag-driven)
    - [x] Add a GitHub Actions release workflow that on tag `v*`:
      - [x] Builds artifacts (AAR, XCFramework).
      - [x] Publishes to Maven Central (Sonatype) after signing.
      - [x] Attaches artifacts to GitHub Release (docs, XCFramework zip).
      - [x] Optionally updates GitHub Pages docs.

14. [ ] Consumer onboarding and samples
   - [x] Provide Android sample (existing `composeApp`) consuming `:pollingengine` from project dependency.
   - [ ] Provide Swift sample (inside `iosApp`) consuming the framework.
   - [ ] Add copy-paste snippets (Gradle, Swift, CocoaPods, SPM) to README.

15. [ ] Security and maintenance
    - [x] Enable Dependabot (Gradle + GitHub Actions).
    - [x] Enable code scanning (GitHub CodeQL) for Kotlin/Swift where applicable.
    - [ ] Add license file and headers.
    - [x] Add `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`, `SECURITY.md`.

---

## Release Playbook — Android (Maven Central)

1. [x] Prepare Sonatype/OSSRH account and GPG signing
   - [x] Create an OSSRH account and request `groupId` ownership (e.g., `in.androidplay`).
   - [x] Generate a GPG key: `gpg --full-generate-key` and publish to a keyserver (e.g., keyserver.ubuntu.com).
   - [x] Store the private key and passphrase securely (CI secrets).

2. [x] Configure Gradle for publishing in `:pollingengine`
   - [x] In `gradle.properties` (user or project), set:
     - [x] `signing.keyId`, `signing.password`, `signing.key` (armored private key),
     - [x] `ossrhUsername`, `ossrhPassword`,
     - [x] `GROUP`, `VERSION_NAME` (or set in build.gradle.kts).
   - [x] Apply plugins: `maven-publish`, `signing` and root `io.github.gradle-nexus.publish-plugin`.
   - [x] Configure publications for Android AAR and metadata (`pom { name, description, url, licenses, scm, developers }`).

3. [x] Dry run locally
   - [x] `./gradlew :pollingengine:publishToSonatype closeAndReleaseSonatypeStagingRepository` (or use staged close first, then release).
   - [x] Verify artifacts and POM contents in staging repository.

4. [x] Release
   - [x] Create a Git tag `vX.Y.Z` and push.
   - [x] CI runs the release workflow to publish to Maven Central.
   - [x] Wait for sync to Maven Central (can take up to ~2 hours).

5. [x] Verify consumption
   - [x] Consume from a sample Android project: `implementation("in.androidplay:pollingengine:X.Y.Z")`.

---

## Release Playbook — iOS via CocoaPods

1. [x] Add CocoaPods support to `:pollingengine`
   - [x] Apply `kotlin("native.cocoapods")` plugin and configure:
     - [x] `summary`, `homepage`, `license`, `authors`, `ios.deploymentTarget`.
     - [x] `framework { baseName = "PollingEngine"; isStatic = true }`.
   - [x] Run `podspec` generation via Gradle: `./gradlew :pollingengine:podspec`.

2. [ ] Validate locally
   - [ ] `pod lib lint PollingEngine.podspec --allow-warnings`.

3. [ ] Publish
   - [ ] Register for CocoaPods trunk if not already.
   - [ ] `pod trunk push PollingEngine.podspec --allow-warnings`.

4. [ ] Consume
   - [ ] In `Podfile`: `pod 'PollingEngine', '~> X.Y'` and run `pod install`.

---

## Release Playbook — iOS via Swift Package Manager (binary XCFramework)

1. [ ] Build XCFramework artifact
   - [ ] Configure iOS targets for `:pollingengine` and ensure `isStatic = true` as needed.
   - [ ] Build: `./gradlew :pollingengine:assembleReleaseXCFramework`.
   - [ ] Zip the `.xcframework` (e.g., `PollingEngine.xcframework.zip`).

2. [ ] Compute checksum and host artifact
   - [ ] Upload the zip to GitHub Releases (or a CDN with HTTPS).
   - [ ] Compute checksum: `swift package compute-checksum PollingEngine.xcframework.zip`.

3. [ ] Create/Update `Package.swift`
   - [ ] Provide a Swift Package with a binary target referencing the URL and checksum of the zip.
   - [ ] Tag the repo `vX.Y.Z` so SPM can resolve the package.

4. [ ] Consume
   - [ ] In Xcode, add the package URL; select version `X.Y.Z`.

---

## Post‑release

1. [ ] Update `CHANGELOG.md` with the released version and notes.
2. [ ] Update README usage snippets with the new version.
3. [ ] Announce release and collect feedback/issues.
