# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning (SemVer).

## [Unreleased]
- CI workflows (build, static checks) and CodeQL added.
- Release workflow scaffolding (tag `v*`) with conditional publishing to Sonatype.
- Binary Compatibility Validator plugin applied (baseline pending).
- Documentation updates and community files (LICENSE, CODE_OF_CONDUCT, CONTRIBUTING, SECURITY).

## [0.1.0] - 2025-09-05
Initial extraction of PollingEngine as a KMP library.
- Kotlin Multiplatform library module `:pollingengine` with Android and iOS (XCFramework) targets.
- Polling engine with backoff, jitter, hooks, and metrics interfaces.
- Sample `composeApp` depending on the library.
