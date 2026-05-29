# android-feature-flag

[![CI](https://github.com/ivmakar/android-feature-flag/actions/workflows/ci.yml/badge.svg)](https://github.com/ivmakar/android-feature-flag/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**English** | [Русский](README.ru.md)

A small, typed, multi-source **feature-flag resolver** for Android.

It layers several flag sources by priority — business defaults, build-variant overrides, persisted QA overrides, and a forced kill-switch — and exposes one value through a synchronous `get()` and a reactive `observe()`. The core is storage-agnostic and has no dependency on your app; flag declarations and persistence wiring live on your side.

```kotlin
object FavoritesEnabled : BoolFeature("favorites_enabled", default = false)

if (featureFlags.get(FavoritesEnabled)) { /* … */ }              // sync
featureFlags.observe(FavoritesEnabled).collect { enabled -> /* … */ }  // reactive
```

---

## Why

- **Typed flags.** A flag is a typed object (`Bool` / `Enum` / `String` / `Int`), not a stringly-typed key. `get(feature)` returns the declared type.
- **Layered resolution.** Production defaults, debug defaults, QA overrides and a kill-switch resolve by priority — and **overrides are debug-only by construction**, so a release build can never serve a QA-set value.
- **Sync and reactive from one source of truth.** `get()` and `observe()` share the same resolution logic.
- **No vendor lock-in in the core.** The resolver, codec and config DSL pull in only Coroutines + DataStore. A ready-made Hilt module is provided but optional — see [Without Hilt](#without-hilt).

---

## Concepts

| Type | Role |
|------|------|
| `Feature<T>` | Sealed flag declaration: `BoolFeature`, `EnumFeature<E>`, `StringFeature`, `IntFeature`. Each carries a `key`, a `default`, and an optional `description`. |
| `FeatureFlags` | Public API: `get(feature): T` (synchronous) and `observe(feature): Flow<T>` (reactive). |
| `FeatureFlagSource` | A source of raw `String?` values for a key. `null` means "no opinion" → the resolver falls through to the next source. |
| `Priority` | `BUSINESS < BUILD_VARIANT < LOCAL_OVERRIDES < FORCED`. Higher rank wins; equal rank → first in list. |
| `FeatureCodec` | The single place that (de)serialises each `Feature` subtype. An undecodable raw value (renamed enum, non-numeric int) decodes to `null` → fall-through. |
| `FeatureConfig` | An immutable map of raw values, built with a typed DSL (`enable` / `disable` / `value`). |
| `FeatureFlagsConfigurator` | Assembles the sources for the current build and produces a `FeatureFlags`. |

### Resolution order

```
FORCED            kill-switch / test escape hatch        ┐ highest
LOCAL_OVERRIDES   PersistentOverrideSource (debug only)  │
BUILD_VARIANT     debug defaults (debug only)            │
BUSINESS          production defaults (always present)   ┘ → else Feature.default
```

`FeatureFlagsConfigurator` registers the build-variant and persistent-override sources **only when `isDebug` is true**. A release build resolves purely from the business defaults (plus a forced kill-switch if you populate one), so no debug default or persisted override can leak into production.

### Sources

- **`BusinessSource`** — your production defaults. Always present.
- **`BuildVariantSource`** — debug-only defaults (e.g. point a server flag at a dev endpoint in debug).
- **`PersistentOverrideSource`** — DataStore-backed QA overrides for debug builds. A `@Volatile` in-memory cache bridges the suspending store to the synchronous `snapshot()`; a single long-lived collector refreshes it and **retries with backoff** on a read failure (a transient error never permanently freezes overrides). It can be primed synchronously at construction so the very first `get()` already sees the persisted value.
- **`ForcedSource`** — top priority; populated only through config. Use it as a kill-switch or a test escape hatch.

---

## Requirements

- Android Gradle Plugin 8.13+, Kotlin 2.3+, Gradle 8.13+
- `minSdk 24`, `compileSdk 36`, Java/JVM 21
- Coroutines, AndroidX DataStore (transitively provided by the module)

---

## Installation (source-only)

This library is distributed as **source**. Pick one of the three ways below.

### A. Git submodule + `include()`

```bash
git submodule add git@github.com:ivmakar/android-feature-flag.git libs/android-feature-flag
```

In your root `settings.gradle.kts`:

```kotlin
include(":feature-flags")
project(":feature-flags").projectDir =
    file("libs/android-feature-flag/feature-flags")
```

Then in the consuming module:

```kotlin
dependencies {
    implementation(project(":feature-flags"))
    testImplementation(testFixtures(project(":feature-flags"))) // FakeFeatureFlags
}
```

> Make sure your root `gradle.properties` has `android.experimental.enableTestFixturesKotlinSupport=true` if you want the test fixtures.

### B. Composite build (`includeBuild`)

```bash
git submodule add git@github.com:ivmakar/android-feature-flag.git libs/android-feature-flag
```

In your root `settings.gradle.kts`:

```kotlin
includeBuild("libs/android-feature-flag")
```

The included build publishes the coordinate `io.github.ivmakar:feature-flags:0.1.0`, so a normal dependency is substituted with the local project:

```kotlin
dependencies {
    implementation("io.github.ivmakar:feature-flags:0.1.0")
}
```

### C. Copy the module

Copy the `feature-flags/` directory into your project (e.g. `your-app/feature-flags/`), `include(":feature-flags")` in `settings.gradle.kts`, and depend on it as in option A. The plugin/dependency versions it expects are listed in [Requirements](#requirements).

---

## Usage

### 1. Declare your flags

Declare each flag as an `object` (a stable, comparable reference usable as a key):

```kotlin
import io.github.ivmakar.featureflags.api.BoolFeature
import io.github.ivmakar.featureflags.api.EnumFeature

object FavoritesEnabled : BoolFeature("favorites_enabled", default = false)
object AuthEnabled      : BoolFeature("auth_enabled", default = true)

enum class ServerType { PROD, DEV, STAGE }
object ServerTypeFlag : EnumFeature<ServerType>("server_type", ServerType.PROD, ServerType::class)
```

### 2. Build the resolver

You can wire it through Hilt (provided) or by hand.

#### With Hilt

The module already provides `FeatureFlags`, the `feature_flags` DataStore and `PersistentOverrideSource`. You only supply the typed configs, the debug flag, and (optionally) a migration set and an error handler.

```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ivmakar.featureflags.config.*
import io.github.ivmakar.featureflags.di.IsDebug
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsBindings {

    @Provides @IsDebug
    fun isDebug(): Boolean = BuildConfig.DEBUG

    @Provides @Singleton
    fun businessConfig() = BusinessFeatureConfig(
        FeatureConfig {
            disable(FavoritesEnabled)
            enable(AuthEnabled)
            value(ServerTypeFlag, ServerType.PROD)
        }
    )

    // Debug-only defaults (registered only when isDebug == true).
    @Provides @Singleton
    fun debugConfig() = DebugFeatureConfig(
        FeatureConfig { value(ServerTypeFlag, ServerType.DEV) }
    )

    @Provides @Singleton
    fun forcedConfig() = ForcedFeatureConfig(FeatureConfig.empty())
}
```

Inject `FeatureFlags` anywhere. To let QA flip overrides at runtime, inject `PersistentOverrideSource` and call its `set(feature, value)`:

```kotlin
class MyViewModel @Inject constructor(
    private val featureFlags: FeatureFlags,
    private val overrides: PersistentOverrideSource, // debug screens only
) {
    val authEnabled = featureFlags.get(AuthEnabled)
    suspend fun forceDev() = overrides.set(ServerTypeFlag, ServerType.DEV)
}
```

Optionally observe internal failures by binding a `FeatureFlagsErrorHandler` (the module declares it `@BindsOptionalOf`):

```kotlin
class TimberErrorHandler @Inject constructor() : FeatureFlagsErrorHandler {
    override fun onError(throwable: Throwable) = Timber.e(throwable, "feature-flags")
}

@Module @InstallIn(SingletonComponent::class)
interface ErrorHandlerBindings {
    @Binds fun bind(impl: TimberErrorHandler): FeatureFlagsErrorHandler
}
```

#### Without Hilt

Ignore the `io.github.ivmakar.featureflags.di` package entirely and build the resolver with `FeatureFlagsConfigurator`. This is exactly the path the unit tests exercise.

```kotlin
import io.github.ivmakar.featureflags.config.FeatureConfig
import io.github.ivmakar.featureflags.config.FeatureFlagsConfigurator
import io.github.ivmakar.featureflags.source.PersistentOverrideSource

// Provide this however your app provides singletons (manual DI, Koin, etc.)
fun provideFeatureFlags(
    isDebug: Boolean,
    dataStore: DataStore<Preferences>, // your own feature_flags store
): FeatureFlags {

    val overrideSource = PersistentOverrideSource(
        dataStore = dataStore,
        primeSynchronously = isDebug,
        onError = { Timber.e(it, "feature-flags") },
    )

    return FeatureFlagsConfigurator(
        isDebug = isDebug,
        overrideSource = overrideSource.takeIf { isDebug },
        business = FeatureConfig {
            disable(FavoritesEnabled)
            enable(AuthEnabled)
            value(ServerTypeFlag, ServerType.PROD)
        },
        debugOverrides = FeatureConfig { value(ServerTypeFlag, ServerType.DEV) },
        forced = FeatureConfig.empty(),
    ).build()
}
```

`FeatureFlagsConfigurator` takes plain `FeatureConfig` values — the `BusinessFeatureConfig` / `DebugFeatureConfig` / `ForcedFeatureConfig` wrappers exist only to disambiguate Hilt providers and are not needed here.

---

## Migrating existing values

If you already persist toggles in another DataStore, contribute a `DataMigration<Preferences>` into the module's migration multibinding (`@IntoSet`) instead of opening a second DataStore on that file — DataStore allows only one active instance per file per process. Declare flag `key`s equal to the legacy keys for an identity copy. (With manual wiring, just pass your migrations to your own `PreferenceDataStoreFactory.create(migrations = …)`.)

---

## Testing

The `testFixtures` source set ships `FakeFeatureFlags` — an in-memory, hot implementation with a type-safe builder:

```kotlin
import io.github.ivmakar.featureflags.fakes.FakeFeatureFlags

val flags = FakeFeatureFlags { put(FavoritesEnabled, true) } // bare FakeFeatureFlags() → all defaults
assertThat(flags.get(FavoritesEnabled)).isTrue()
flags.set(AuthEnabled, false) // drives observe() re-emission
```

Run the library's own tests:

```bash
./gradlew :feature-flags:testDebugUnitTest
```

---

## License

[Apache License 2.0](LICENSE). Copyright 2026 ivmakar.
