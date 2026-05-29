# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0]

Initial release.

### Added
- Typed, multi-source feature-flag resolver with priority ordering
  (`BUSINESS < BUILD_VARIANT < LOCAL_OVERRIDES < FORCED`).
- `Feature<T>` hierarchy (`BoolFeature`, and other typed subtypes) with a single
  (de)serialisation point in `FeatureCodec`.
- Synchronous `get()` and reactive `observe()` resolving through the same logic.
- Sources: `BusinessSource`, `BuildVariantSource`, `ForcedSource`,
  `PersistentOverrideSource` (DataStore-backed, debug-only).
- `FeatureFlagsConfigurator` for manual (Hilt-free) wiring; optional Hilt module in `di/`.
- `FakeFeatureFlags` published via the `testFixtures` source set for consumers' tests.

[Unreleased]: https://github.com/ivmakar/android-feature-flag/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ivmakar/android-feature-flag/releases/tag/v0.1.0
