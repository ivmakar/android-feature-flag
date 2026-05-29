# android-feature-flag

[![CI](https://github.com/ivmakar/android-feature-flag/actions/workflows/ci.yml/badge.svg)](https://github.com/ivmakar/android-feature-flag/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) | **Русский**

Небольшой типизированный многоисточниковый **резолвер фичефлагов** для Android.

Он накладывает несколько источников флагов по приоритету — бизнес-дефолты, оверрайды под build-вариант, сохранённые QA-оверрайды и принудительный kill-switch — и отдаёт одно значение через синхронный `get()` и реактивный `observe()`. Ядро не зависит от хранилища и от вашего приложения; объявления флагов и привязка к persistence живут на вашей стороне.

```kotlin
object FavoritesEnabled : BoolFeature("favorites_enabled", default = false)

if (featureFlags.get(FavoritesEnabled)) { /* … */ }              // синхронно
featureFlags.observe(FavoritesEnabled).collect { enabled -> /* … */ }  // реактивно
```

---

## Зачем

- **Типизированные флаги.** Флаг — это типизированный объект (`Bool` / `Enum` / `String` / `Int`), а не строковый ключ. `get(feature)` возвращает объявленный тип.
- **Послойное разрешение.** Продакшен-дефолты, debug-дефолты, QA-оверрайды и kill-switch разрешаются по приоритету — и **оверрайды по построению доступны только в debug**, поэтому release-сборка никогда не отдаст QA-выставленное значение.
- **Синхронность и реактивность из одного источника истины.** `get()` и `observe()` используют одну и ту же логику разрешения.
- **Никакого vendor lock-in в ядре.** Резолвер, кодек и config-DSL тянут только Coroutines + DataStore. Готовый Hilt-модуль есть, но опционален — см. [Без Hilt](#без-hilt).

---

## Понятия

| Тип | Роль |
|------|------|
| `Feature<T>` | Sealed-объявление флага: `BoolFeature`, `EnumFeature<E>`, `StringFeature`, `IntFeature`. Каждый несёт `key`, `default` и опциональный `description`. |
| `FeatureFlags` | Публичный API: `get(feature): T` (синхронно) и `observe(feature): Flow<T>` (реактивно). |
| `FeatureFlagSource` | Источник сырых значений `String?` по ключу. `null` означает «нет мнения» → резолвер проваливается к следующему источнику. |
| `Priority` | `BUSINESS < BUILD_VARIANT < LOCAL_OVERRIDES < FORCED`. Выше ранг — выигрывает; равный ранг → первый в списке. |
| `FeatureCodec` | Единственное место, где (де)сериализуется каждый подтип `Feature`. Недекодируемое сырое значение (переименованный enum, нечисловой int) декодируется в `null` → проваливание. |
| `FeatureConfig` | Неизменяемая карта сырых значений, собираемая типизированным DSL (`enable` / `disable` / `value`). |
| `FeatureFlagsConfigurator` | Собирает источники под текущую сборку и создаёт `FeatureFlags`. |

### Порядок разрешения

```
FORCED            kill-switch / лазейка для тестов        ┐ высший
LOCAL_OVERRIDES   PersistentOverrideSource (только debug) │
BUILD_VARIANT     debug-дефолты (только debug)            │
BUSINESS          продакшен-дефолты (всегда есть)         ┘ → иначе Feature.default
```

`FeatureFlagsConfigurator` регистрирует источники build-варианта и persistent-оверрайдов **только когда `isDebug == true`**. Release-сборка разрешается чисто из бизнес-дефолтов (плюс принудительный kill-switch, если вы его заполните), поэтому ни debug-дефолт, ни сохранённый оверрайд не могут протечь в продакшен.

### Источники

- **`BusinessSource`** — ваши продакшен-дефолты. Присутствуют всегда.
- **`BuildVariantSource`** — дефолты только для debug (например, направить флаг сервера на dev-эндпоинт в debug).
- **`PersistentOverrideSource`** — QA-оверрайды на базе DataStore для debug-сборок. `@Volatile`-кэш в памяти связывает suspend-хранилище с синхронным `snapshot()`; единственный долгоживущий коллектор обновляет его и **повторяет с backoff** при ошибке чтения (временная ошибка никогда не замораживает оверрайды навсегда). Его можно синхронно прогреть при конструировании, чтобы самый первый `get()` уже видел сохранённое значение.
- **`ForcedSource`** — высший приоритет; заполняется только через config. Используйте как kill-switch или лазейку для тестов.

---

## Требования

- Android Gradle Plugin 8.13+, Kotlin 2.3+, Gradle 8.13+
- `minSdk 24`, `compileSdk 36`, Java/JVM 21
- Coroutines, AndroidX DataStore (предоставляются модулем транзитивно)

---

## Установка (только исходники)

Библиотека распространяется как **исходный код**. Выберите один из трёх способов ниже.

### A. Git-сабмодуль + `include()`

```bash
git submodule add git@github.com:ivmakar/android-feature-flag.git libs/android-feature-flag
```

В корневом `settings.gradle.kts`:

```kotlin
include(":feature-flags")
project(":feature-flags").projectDir =
    file("libs/android-feature-flag/feature-flags")
```

Затем в потребляющем модуле:

```kotlin
dependencies {
    implementation(project(":feature-flags"))
    testImplementation(testFixtures(project(":feature-flags"))) // FakeFeatureFlags
}
```

> Убедитесь, что в корневом `gradle.properties` есть `android.experimental.enableTestFixturesKotlinSupport=true`, если нужны test fixtures.

### B. Composite build (`includeBuild`)

```bash
git submodule add git@github.com:ivmakar/android-feature-flag.git libs/android-feature-flag
```

В корневом `settings.gradle.kts`:

```kotlin
includeBuild("libs/android-feature-flag")
```

Включённая сборка публикует координату `io.github.ivmakar:feature-flags:0.1.0`, поэтому обычная зависимость подменяется локальным проектом:

```kotlin
dependencies {
    implementation("io.github.ivmakar:feature-flags:0.1.0")
}
```

### C. Скопировать модуль

Скопируйте каталог `feature-flags/` в свой проект (например, `your-app/feature-flags/`), пропишите `include(":feature-flags")` в `settings.gradle.kts` и подключите как в варианте A. Версии плагинов/зависимостей, которые он ожидает, перечислены в [Требованиях](#требования).

---

## Подключение через AI-агента

Если вы разрабатываете с AI-агентом (Claude Code, Cursor, Copilot и т.д.), скопируйте промпт ниже и передайте его агенту. Он выполнит всю интеграцию сам — добавит библиотеку, настроит сборку, объявит стартовый флаг и проверит сборку.

````text
Интегрируй библиотеку `android-feature-flag` в ЭТОТ проект, от начала до конца.

Источник истины: https://github.com/ivmakar/android-feature-flag
Сначала прочитай `libs/android-feature-flag/README.md` и узнай точный API, прежде чем что-либо подключать.

Шаги:
1. Добавь библиотеку как git-сабмодуль, зафиксированный на последнем release-теге, и подключи composite build:
   - `git submodule add https://github.com/ivmakar/android-feature-flag.git libs/android-feature-flag`
   - В корневой `settings.gradle.kts` добавь: `includeBuild("libs/android-feature-flag")`
   - Убедись, что в корневом `gradle.properties` есть: `android.experimental.enableTestFixturesKotlinSupport=true`
2. В модуле, который должен владеть фичефлагами, добавь зависимости:
   - `implementation("io.github.ivmakar:feature-flags:0.1.0")`
   - `testImplementation(testFixtures("io.github.ivmakar:feature-flags:0.1.0"))`
3. Объяви один стартовый флаг как Kotlin `object`, наследующий `BoolFeature` (стабильный `key` + `default`).
4. Определи, как в проекте сделан DI, и подключи резолвер соответственно:
   - Если есть Hilt: создай Hilt-модуль, предоставляющий `@IsDebug` (= `BuildConfig.DEBUG`), `BusinessFeatureConfig`, debug-only `DebugFeatureConfig` и `ForcedFeatureConfig`.
   - Иначе: собери вручную через `FeatureFlagsConfigurator`, передав `isDebug = BuildConfig.DEBUG`, конфиг `business` и `overrideSource`, который не-null ТОЛЬКО в debug.
5. Покажи одно реальное место вызова: замени существующий захардкоженный boolean-тумблер на `featureFlags.get(<Flag>)`, либо добавь явно помеченный пример вызова, если такого нет.
6. Собери потребляющий модуль и прогони его юнит-тесты; чини привязку, пока они не пройдут.

Жёсткие ограничения:
- `BuildVariantSource` и `PersistentOverrideSource` — ТОЛЬКО для debug. Никогда не позволяй debug-дефолту или сохранённому оверрайду попасть в release-сборку.
- Держи флаги типизированными — объявляй типизированные `Feature`-объекты, никаких сырых строковых ключей.
- Ничего не меняй внутри `libs/android-feature-flag/`; считай это read-only зависимостью.
````

> Замени `0.1.0` на последний release-тег и зафиксируй сабмодуль на этом теге.

---

## Использование

### 1. Объявите свои флаги

Объявите каждый флаг как `object` (стабильная, сравнимая ссылка, пригодная как ключ):

```kotlin
import io.github.ivmakar.featureflags.api.BoolFeature
import io.github.ivmakar.featureflags.api.EnumFeature

object FavoritesEnabled : BoolFeature("favorites_enabled", default = false)
object AuthEnabled      : BoolFeature("auth_enabled", default = true)

enum class ServerType { PROD, DEV, STAGE }
object ServerTypeFlag : EnumFeature<ServerType>("server_type", ServerType.PROD, ServerType::class)
```

### 2. Соберите резолвер

Можно подключить через Hilt (предоставляется) или вручную.

#### С Hilt

Модуль уже предоставляет `FeatureFlags`, DataStore `feature_flags` и `PersistentOverrideSource`. Вы поставляете только типизированные конфиги, debug-флаг и (опционально) набор миграций и обработчик ошибок.

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

    // Debug-дефолты (регистрируются только когда isDebug == true).
    @Provides @Singleton
    fun debugConfig() = DebugFeatureConfig(
        FeatureConfig { value(ServerTypeFlag, ServerType.DEV) }
    )

    @Provides @Singleton
    fun forcedConfig() = ForcedFeatureConfig(FeatureConfig.empty())
}
```

Инжектьте `FeatureFlags` где угодно. Чтобы QA мог переключать оверрайды в рантайме, заинжектьте `PersistentOverrideSource` и вызывайте его `set(feature, value)`:

```kotlin
class MyViewModel @Inject constructor(
    private val featureFlags: FeatureFlags,
    private val overrides: PersistentOverrideSource, // только debug-экраны
) {
    val authEnabled = featureFlags.get(AuthEnabled)
    suspend fun forceDev() = overrides.set(ServerTypeFlag, ServerType.DEV)
}
```

Опционально наблюдайте внутренние сбои, привязав `FeatureFlagsErrorHandler` (модуль объявляет его через `@BindsOptionalOf`):

```kotlin
class TimberErrorHandler @Inject constructor() : FeatureFlagsErrorHandler {
    override fun onError(throwable: Throwable) = Timber.e(throwable, "feature-flags")
}

@Module @InstallIn(SingletonComponent::class)
interface ErrorHandlerBindings {
    @Binds fun bind(impl: TimberErrorHandler): FeatureFlagsErrorHandler
}
```

#### Без Hilt

Полностью игнорируйте пакет `io.github.ivmakar.featureflags.di` и стройте резолвер через `FeatureFlagsConfigurator`. Это ровно тот путь, который проверяют юнит-тесты.

```kotlin
import io.github.ivmakar.featureflags.config.FeatureConfig
import io.github.ivmakar.featureflags.config.FeatureFlagsConfigurator
import io.github.ivmakar.featureflags.source.PersistentOverrideSource

// Предоставьте это так, как ваше приложение предоставляет синглтоны (ручной DI, Koin и т.д.)
fun provideFeatureFlags(
    isDebug: Boolean,
    dataStore: DataStore<Preferences>, // ваш собственный store feature_flags
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

`FeatureFlagsConfigurator` принимает обычные значения `FeatureConfig` — обёртки `BusinessFeatureConfig` / `DebugFeatureConfig` / `ForcedFeatureConfig` существуют лишь для того, чтобы развести Hilt-провайдеры, и здесь не нужны.

---

## Миграция существующих значений

Если вы уже храните тумблеры в другом DataStore, добавьте `DataMigration<Preferences>` в migration-multibinding модуля (`@IntoSet`), а не открывайте второй DataStore на том же файле — DataStore допускает только один активный инстанс на файл на процесс. Объявите `key` флагов равными legacy-ключам для копии «один в один». (При ручной привязке просто передайте свои миграции в собственный `PreferenceDataStoreFactory.create(migrations = …)`.)

---

## Тестирование

Source set `testFixtures` поставляет `FakeFeatureFlags` — in-memory hot-реализацию с типобезопасным билдером:

```kotlin
import io.github.ivmakar.featureflags.fakes.FakeFeatureFlags

val flags = FakeFeatureFlags { put(FavoritesEnabled, true) } // голый FakeFeatureFlags() → все дефолты
assertThat(flags.get(FavoritesEnabled)).isTrue()
flags.set(AuthEnabled, false) // вызывает повторную эмиссию observe()
```

Запустить собственные тесты библиотеки:

```bash
./gradlew :feature-flags:testDebugUnitTest
```

---

## Лицензия

[Apache License 2.0](LICENSE). Copyright 2026 ivmakar.
