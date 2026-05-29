# android-feature-flag

Standalone, reusable feature-flag library for Android. Typed, multi-source resolver. No dependency on any specific application — keep it that way.

## Build & Test

```bash
./gradlew :feature-flags:testDebugUnitTest    # unit tests (the gate — must stay green)
./gradlew :feature-flags:assembleRelease       # build the AAR
./gradlew :feature-flags:lint                  # lint
```

A change is complete only when `:feature-flags:testDebugUnitTest` passes.

## Layout

```
feature-flags/                         ← the only module (com.android.library)
  src/main/java/io/github/ivmakar/featureflags/
    api/      — Feature<T> hierarchy, FeatureFlags, FeatureFlagSource, Priority, FeatureFlagsErrorHandler
    codec/    — FeatureCodec: the single (de)serialisation point for every Feature subtype
    config/   — FeatureConfig + DSL builder, the Hilt wrapper types, FeatureFlagsConfigurator
    impl/     — FeatureFlagsImpl: priority-ordered resolver, get()/observe()
    source/   — Business / BuildVariant / Forced / PersistentOverrideSource
    di/       — FeatureFlagsModule (Hilt), qualifiers (@IsDebug, @FeatureFlagsDataStore)
  src/test/         — JUnit4 + MockK + Truth + Turbine + coroutines-test
  src/testFixtures/ — FakeFeatureFlags (published for consumers' tests)
```

## Architecture invariants

- **`get()` and `observe()` resolve through the same logic** (`FeatureFlagsImpl.resolve`). Never let them diverge.
- **Priority order:** `BUSINESS < BUILD_VARIANT < LOCAL_OVERRIDES < FORCED`. Higher rank wins; equal rank → first in list (stable sort).
- **Debug-only sources.** `FeatureFlagsConfigurator` registers `BuildVariantSource` and `PersistentOverrideSource` only when `isDebug`. A release build must resolve from `BusinessSource` (+ forced) only — never let a debug default or persisted override reach release.
- **Sources speak `String?`.** `null` = "no opinion" → fall through. All typing lives in `FeatureCodec`; an undecodable raw value decodes to `null` (fall-through), never a crash.
- **`FeatureCodec.decode`'s `@Suppress("UNCHECKED_CAST")` is safe** only because dispatch is on the sealed `Feature` subtype, which statically fixes `T`. Preserve that property if you touch the codec.
- **`PersistentOverrideSource`** bridges a suspending DataStore to a synchronous `snapshot()` via a `@Volatile` whole-map cache fed by one long-lived collector. On read failure it reports via `onError` and **retries with backoff** — do not turn that back into a terminal `catch`. Cache swaps must stay whole-map (no in-place mutation) so readers never see a half-rebuilt map.

## Conventions

- **Keep it app-agnostic.** No references to a specific app/module (`:app`, vendor names, app types) in code or comments. Examples belong in `README.md`.
- **Hilt is optional for consumers.** The `di/` package is a convenience. Anything reachable only through Hilt must also be wireable by hand via `FeatureFlagsConfigurator` — verify the manual path stays intact.
- **Comments:** explain *why* / invariants on the non-trivial bits (resolver ordering, cache/volatile, codec cast, retry). Trivial code stays uncommented.
- **Tests:** JUnit4, Truth assertions, Turbine for flows, `UnconfinedTestDispatcher`/`runTest`, `TemporaryFolder` for DataStore. Prefer fakes over mocks. Never mock `kotlin.Result` (inline-class `ClassCastException`) — use real `Result.success`/`failure`.
- **Public API changes** ripple: a new `Feature` subtype needs a `FeatureCodec` branch (the `when` is exhaustive — the compiler enforces it) and round-trip tests.

## Versions

Gradle 8.13, AGP 8.13.2, Kotlin 2.3.0, KSP 2.2.20-2.0.3, Hilt 2.57.2, DataStore 1.2.0, Coroutines 1.10.2. Dependency versions are hardcoded in `feature-flags/build.gradle.kts` (no version catalog).

## Distribution

Source-only: consumers add this repo as a git submodule (`include()` or `includeBuild`) or copy the `feature-flags/` module. Composite-build coordinate: `io.github.ivmakar:feature-flags:<version>` (set in `feature-flags/build.gradle.kts`). Bump `version` there on releases and tag.
