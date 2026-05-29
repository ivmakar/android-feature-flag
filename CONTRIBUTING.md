# Contributing to android-feature-flag

Thanks for your interest in improving this library. This document covers how to build, test, and submit changes.

## Ground rules

- **Keep it app-agnostic.** This is a standalone, reusable library. No references to a specific app, module (`:app`), or vendor name in code or comments. Concrete examples belong in `README.md`.
- **A change is complete only when `:feature-flags:testDebugUnitTest` passes.** That suite is the gate, both locally and in CI.
- **Public API changes ripple.** A new `Feature` subtype needs a matching `FeatureCodec` branch (the `when` is exhaustive — the compiler enforces it) and round-trip tests.

## Prerequisites

- JDK 21
- Android SDK (`compileSdk 36`, `minSdk 24`)

The Gradle wrapper is committed, so no separate Gradle install is needed.

## Build & test

```bash
./gradlew :feature-flags:testDebugUnitTest    # unit tests — the gate, must stay green
./gradlew :feature-flags:lint                  # lint
./gradlew :feature-flags:assembleRelease       # build the AAR
```

## Tests

- JUnit4 + Truth assertions + Turbine for flows + `UnconfinedTestDispatcher`/`runTest`.
- `TemporaryFolder` for DataStore-backed tests.
- Prefer fakes over mocks.
- Never mock `kotlin.Result` (inline-class `ClassCastException`) — use real `Result.success`/`Result.failure`.

New behavior needs tests. Bug fixes need a regression test that fails before the fix.

## Architecture invariants

Read the `## Architecture invariants` section in `CLAUDE.md` before touching the resolver, codec, or `PersistentOverrideSource`. Key points:

- `get()` and `observe()` resolve through the same logic (`FeatureFlagsImpl.resolve`) — never let them diverge.
- Priority order: `BUSINESS < BUILD_VARIANT < LOCAL_OVERRIDES < FORCED`.
- Debug-only sources must never reach a release build.
- Sources speak `String?`; all typing lives in `FeatureCodec`; an undecodable value decodes to `null`, never a crash.

## Commit messages

Format: `ACT scope -- what`. Codes: `ADD`, `UPD`, `DEL`, `FEA`, `FAC` (refactor), `FIX`, `FRM` (format), `DOC`.

Example: `FIX resolver -- equal-rank ties now resolve by list order`

## Pull requests

1. Fork and branch from `main`.
2. Make your change with tests.
3. Run the gate (`testDebugUnitTest`) and `lint` locally.
4. Open a PR describing **what** changed and **why**. Link any related issue.

CI runs tests, lint, and an AAR build on every PR. Keep PRs focused — one logical change per PR.

## Reporting bugs / requesting features

Open an issue. For bugs, include: library version, a minimal repro, expected vs. actual behavior.

## License

By contributing, you agree your contributions are licensed under the [Apache License 2.0](LICENSE).
