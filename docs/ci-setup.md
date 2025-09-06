# [ARCHIVED] CI and Release Setup Guide

Note: CI workflows and publishing configuration have been removed from this repository as part of a
cleanup. This document is retained for reference only. Do not commit credentials or workflow files.

Last updated: 2025-09-07 02:33

This document explains how to set up Continuous Integration (CI) for this repository and how to publish the PollingEngine libraries to Maven Central (Android/KMP) and CocoaPods (iOS). It assumes you are using GitHub as the hosting platform.

Context: The Gradle build for `:pollingengine` already includes publishing and signing configuration, Dokka docs, Detekt, ktlint, and CocoaPods support. CI will orchestrate tasks and provide credentials via secrets.

---

## 1) Prerequisites

- GitHub repository admin access
- Java 11 (used by the build)
- A Mac runner is required for iOS/Kotlin/Native tasks (GitHub-hosted `macos-latest` works)
- Maven Central Portal account with access to your groupId and Central Portal credentials (e.g.,
  `in.androidplay`)
- GPG key for signing artifacts (public key published to a keyserver)
- CocoaPods installed locally for validation (optional in CI, but required for `pod trunk push`)

---

## 2) Local setup (for maintainers)

These steps help you validate before pushing tags that trigger release.

1. Configure your local `~/.gradle/gradle.properties` with the following (do not commit this file):
   - signing.keyId=YOUR_KEY_ID
   - signing.password=YOUR_GPG_PASSPHRASE
   - signing.key=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
   # Optional legacy path (not recommended): if you have a key ring file, you can set
   # signing.secretKeyRingFile=/path/to/your_secret.gpg
   # The build will load it into memory; no
   `gpg` binary is required. Do NOT set signing.useGpg unless you want to use system gpg.
    - mavenCentralUsername=YOUR_CENTRAL_PORTAL_USERNAME
    - mavenCentralPassword=YOUR_CENTRAL_PORTAL_PASSWORD

2. Dry-run a local publish (legacy direct):
    - ./gradlew :pollingengine:publish

3. Generate docs locally:
   - ./gradlew :pollingengine:dokkaHtml

4. CocoaPods podspec generation and lint (optional):
   - ./gradlew :pollingengine:podspec
   - pod lib lint pollingengine/pollingengine.podspec --allow-warnings

---

## 3) Required GitHub Secrets

Set these secrets in your GitHub repository under Settings → Security → Secrets and variables → Actions → New repository secret.

Core publishing:

- MAVEN_CENTRAL_USERNAME: Central Portal username
- MAVEN_CENTRAL_PASSWORD: Central Portal password
- SIGNING_KEY_ID: Your GPG key ID (short or long ID as used by Gradle signing)
- SIGNING_PASSWORD: Passphrase for the GPG private key
- SIGNING_KEY: ASCII-armored GPG private key contents (single line or multiline; ensure proper YAML quoting in workflow if needed)

Optional (documentation publishing, if enabled in workflows):
- GH_PAGES_TOKEN: A token with permissions to push to gh-pages (PAT if GITHUB_TOKEN permissions aren’t sufficient)

Optional (CocoaPods trunk):
- COCOAPODS_TRUNK_TOKEN: CocoaPods trunk token (after `pod trunk register`)

Notes:
- GitHub provides GITHUB_TOKEN automatically; it usually has repo-scoped permissions sufficient for creating releases and uploading assets if configured in the workflow permissions.

---

## 4) Environment variables used by Gradle

The Gradle signing and publishing configuration will read credentials from Gradle properties or environment variables. In CI, we typically export the GitHub Secrets as env vars so Gradle can discover them:

- ORG_GRADLE_PROJECT_signing.keyId → SIGNING_KEY_ID
- ORG_GRADLE_PROJECT_signing.password → SIGNING_PASSWORD
- ORG_GRADLE_PROJECT_signing.key → SIGNING_KEY
- ORG_GRADLE_PROJECT_mavenCentralUsername → MAVEN_CENTRAL_USERNAME
- ORG_GRADLE_PROJECT_mavenCentralPassword → MAVEN_CENTRAL_PASSWORD

Workflows should map secrets to these env vars, e.g.:
- env:
  - ORG_GRADLE_PROJECT_signing.keyId: ${{ secrets.SIGNING_KEY_ID }}
  - ORG_GRADLE_PROJECT_signing.password: ${{ secrets.SIGNING_PASSWORD }}
  - ORG_GRADLE_PROJECT_signing.key: ${{ secrets.SIGNING_KEY }}
  - ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
  - ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}

This allows Gradle to pick them up without committing anything sensitive.

---

## 5) Suggested GitHub Actions workflows

Although not committed here, you can create the following workflows under `.github/workflows/`.

A) PR and main build (quality gates):
- Runs on push/pull_request to main branches
- Ubuntu job: `./gradlew build detekt ktlintCheck apiCheck dokkaHtml`
- macOS job: `./gradlew :pollingengine:assembleReleaseXCFramework` (optional) and tests
- Cache Gradle and Kotlin/Native

B) Release workflow (tag-driven):
- Trigger: push tag `v*` (e.g., `v0.1.0`)
- macOS runner (needed for K/N):
  - Set env vars from secrets (see section 4)
  - Build artifacts:
    `./gradlew clean build :pollingengine:assembleReleaseXCFramework :pollingengine:publish :pollingengine:dokkaHtml`
  - Upload Dokka site and build artifacts as GitHub Release assets (optional)
  - Optionally publish gh-pages docs if you maintain a docs site

C) Optional CocoaPods publish job:
- Runs on tag `v*` or manual dispatch
- Steps:
  - ./gradlew :pollingengine:podspec
  - pod trunk push pollingengine/pollingengine.podspec --allow-warnings
- Requires `COCOAPODS_TRUNK_TOKEN` secret and a macOS runner with CocoaPods installed (use `brew install cocoapods` step if needed).

---

## 6) Releasing

1) Tag-based release (Maven Central):
- Ensure `group` and `version` in `pollingengine/build.gradle.kts` are correct
- Update CHANGELOG.md
- Create and push a tag `vX.Y.Z`:
  - git tag v0.1.0
  - git push origin v0.1.0
- CI release workflow publishes to Maven Central (Central Portal) and closes/releases the staging
  repository
- Wait for Maven Central sync (can take up to ~2 hours)

2) CocoaPods release:
- Ensure the generated `pollingengine.podspec` has the right version and metadata
- Validate locally: `pod lib lint pollingengine/pollingengine.podspec --allow-warnings`
- Publish: `pod trunk push pollingengine/pollingengine.podspec --allow-warnings`
- Consumers can then `pod 'PollingEngine', '~> X.Y'`

3) Docs and assets (optional):
- Upload the XCFramework zip and docs to the GitHub Release page or publish docs to GitHub Pages

---

## 7) Local validation checklist before cutting a release

- ./gradlew clean build
- ./gradlew :pollingengine:dokkaHtml
- ./gradlew :pollingengine:assembleReleaseXCFramework
- ./gradlew :pollingengine:podspec
- pod lib lint pollingengine/pollingengine.podspec --allow-warnings (optional)
- ./gradlew :pollingengine:publishAllPublicationsToMavenCentralRepository :pollingengine:
  closeAndReleaseMavenCentralStagingRepository (optional dry-run with real creds)

---

## 8) Troubleshooting

- Signing errors (e.g., "No appropriate signing key"): Verify SIGNING_KEY, SIGNING_KEY_ID, and SIGNING_PASSWORD are set and correctly mapped to ORG_GRADLE_PROJECT_* env vars.
- Central Portal errors (401/403): Verify MAVEN_CENTRAL_USERNAME/MAVEN_CENTRAL_PASSWORD and groupId
  ownership.
- iOS build failures on Linux runners: Use macOS runners for Kotlin/Native iOS tasks.
- CocoaPods push rejection: Ensure the spec version matches a git tag, the homepage and source URLs are reachable, and that you’re registered on CocoaPods trunk.
- Dokka task issues: Ensure the Dokka plugin version matches the Kotlin version; re-run `./gradlew :pollingengine:dokkaHtml` with `--info` for details.

---

## 9) Reference tasks in this repo

Common Gradle tasks you’ll use:
- Build and test: `./gradlew build`
- Lint and static analysis: `./gradlew detekt ktlintCheck`
- API check (if configured): `./gradlew apiCheck`
- Dokka docs: `./gradlew :pollingengine:dokkaHtml`
- Publish to Maven Central (legacy direct): `./gradlew :pollingengine:publish`
- Generate Podspec: `./gradlew :pollingengine:podspec`
- Build XCFramework: `./gradlew :pollingengine:assembleReleaseXCFramework`

If you need concrete workflow YAML examples later, you can copy the env var mappings in Section 4 directly into your `.github/workflows/release.yml`.
