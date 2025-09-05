# Contributing to PollingEngine

Thanks for your interest in contributing! Please follow these guidelines to help us keep the project healthy and maintainable.

## Development setup
- Use the latest Android Studio or IntelliJ IDEA with Kotlin Multiplatform support.
- JDK 17 is required for builds and CI.
- Run `./gradlew build` to verify.

## Code style and static checks
- Ktlint and Detekt are enabled. Run:
  - `./gradlew ktlintFormat ktlintCheck`
  - `./gradlew detekt`
- Keep changes small and focused; add tests where possible.

## Commit messages and PRs
- Use clear, descriptive commit messages.
- Open PRs against `main` and ensure CI passes.
- Link related issues and describe the rationale for changes.

## API stability
- The library uses semantic versioning (MAJOR.MINOR.PATCH).
- Public API changes should be intentional; Binary Compatibility Validator (apiCheck) runs in CI.

## Reporting issues
- Provide reproduction steps, expected vs actual behavior, and environment details.

## License
By contributing, you agree that your contributions will be licensed under the Apache-2.0 License.