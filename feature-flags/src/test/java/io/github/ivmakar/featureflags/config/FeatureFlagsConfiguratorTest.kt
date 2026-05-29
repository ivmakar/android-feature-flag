package io.github.ivmakar.featureflags.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.ivmakar.featureflags.TestBool
import io.github.ivmakar.featureflags.source.PersistentOverrideSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsConfiguratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun overrideSource(scope: TestScope): PersistentOverrideSource {
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                tmp.newFile("configurator_${System.nanoTime()}.preferences_pb")
            }
        return PersistentOverrideSource(store, scope.backgroundScope)
    }

    @Test
    fun `debug build registers persistent override so override wins`() =
        runTest(StandardTestDispatcher()) {
            val override = overrideSource(this)
            // BUSINESS says false; an active LOCAL_OVERRIDES source set to true should win.
            override.set(TestBool.key, "true")
            runCurrent()

            val flags = FeatureFlagsConfigurator(
                isDebug = true,
                overrideSource = override,
                business = FeatureConfig { disable(TestBool) },
            ).build()

            assertThat(flags.get(TestBool)).isTrue()
        }

    @Test
    fun `production build ignores persistent override even when provided`() =
        runTest(StandardTestDispatcher()) {
            val override = overrideSource(this)
            override.set(TestBool.key, "true")
            runCurrent()

            // The configurator must drop the override source when isDebug=false: business default wins.
            val flags = FeatureFlagsConfigurator(
                isDebug = false,
                overrideSource = override,
                business = FeatureConfig { disable(TestBool) },
            ).build()

            assertThat(flags.get(TestBool)).isFalse()
        }

    @Test
    fun `build variant source active only in debug`() = runTest(StandardTestDispatcher()) {
        val business = FeatureConfig { disable(TestBool) }
        val debugOverrides = FeatureConfig { enable(TestBool) }

        val debugFlags = FeatureFlagsConfigurator(
            isDebug = true,
            overrideSource = null,
            business = business,
            debugOverrides = debugOverrides,
        ).build()
        val releaseFlags = FeatureFlagsConfigurator(
            isDebug = false,
            overrideSource = null,
            business = business,
            debugOverrides = debugOverrides,
        ).build()

        assertThat(debugFlags.get(TestBool)).isTrue()
        assertThat(releaseFlags.get(TestBool)).isFalse()
    }

    @Test
    fun `forced source wins over everything`() = runTest(StandardTestDispatcher()) {
        val flags = FeatureFlagsConfigurator(
            isDebug = true,
            overrideSource = null,
            business = FeatureConfig { enable(TestBool) },
            debugOverrides = FeatureConfig { enable(TestBool) },
            forced = FeatureConfig { disable(TestBool) },
        ).build()
        assertThat(flags.get(TestBool)).isFalse()
    }

    @Test
    fun `forced source is registered and wins in a release build`() = runTest(StandardTestDispatcher()) {
        // ForcedSource is added unconditionally (kill-switch must work in release, where the build-
        // variant and persistent-override sources are dropped). Business says true; a forced
        // disable must still win with isDebug = false.
        val flags = FeatureFlagsConfigurator(
            isDebug = false,
            overrideSource = null,
            business = FeatureConfig { enable(TestBool) },
            forced = FeatureConfig { disable(TestBool) },
        ).build()
        assertThat(flags.get(TestBool)).isFalse()
    }
}
