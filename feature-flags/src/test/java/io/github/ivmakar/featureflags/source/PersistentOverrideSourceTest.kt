package io.github.ivmakar.featureflags.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import io.github.ivmakar.featureflags.TestBool
import io.github.ivmakar.featureflags.TestEnum
import io.github.ivmakar.featureflags.TestServerType
import io.github.ivmakar.featureflags.config.FeatureConfig
import io.github.ivmakar.featureflags.config.FeatureFlagsConfigurator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentOverrideSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dataStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            tmp.newFile("feature_flags_test_${System.nanoTime()}.preferences_pb")
        }

    @Test
    fun `snapshot reflects set and remove`() = runTest(UnconfinedTestDispatcher()) {
        val source = PersistentOverrideSource(dataStore(this), backgroundScope)

        source.set(TestBool.key, "true")
        runCurrent()
        assertThat(source.snapshot(TestBool.key)).isEqualTo("true")

        source.remove(TestBool.key)
        runCurrent()
        assertThat(source.snapshot(TestBool.key)).isNull()
    }

    @Test
    fun `observe re-emits on persistent override change`() = runTest(UnconfinedTestDispatcher()) {
        val source = PersistentOverrideSource(dataStore(this), backgroundScope)
        val flags = FeatureFlagsConfigurator(
            isDebug = true,
            overrideSource = source,
            business = FeatureConfig { disable(TestBool) },
        ).build()

        flags.observe(TestBool).test {
            assertThat(awaitItem()).isFalse() // initial: business default

            source.set(TestBool.key, "true")
            assertThat(awaitItem()).isTrue() // override published

            source.remove(TestBool.key)
            assertThat(awaitItem()).isFalse() // back to business default

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typed set round-trips an enum through the codec`() = runTest(UnconfinedTestDispatcher()) {
        val source = PersistentOverrideSource(dataStore(this), backgroundScope)
        val flags = FeatureFlagsConfigurator(
            isDebug = true,
            overrideSource = source,
            business = FeatureConfig { value(TestEnum, TestServerType.PROD) },
        ).build()

        source.set(TestEnum, TestServerType.STAGE)
        runCurrent()

        // Stored via the typed overload, read back decoded — proves the typed set/encode path, not
        // just the raw string one.
        assertThat(source.snapshot(TestEnum.key)).isEqualTo("STAGE")
        assertThat(flags.get(TestEnum)).isEqualTo(TestServerType.STAGE)
    }

    @Test
    fun `read failure is reported to onError and degrades to no opinion`() =
        runTest(UnconfinedTestDispatcher()) {
            val failing = object : DataStore<Preferences> {
                override val data = flow<Preferences> { throw IOException("corrupt store") }
                override suspend fun updateData(
                    transform: suspend (Preferences) -> Preferences,
                ): Preferences = throw UnsupportedOperationException()
            }

            var captured: Throwable? = null
            val source = PersistentOverrideSource(failing, backgroundScope, onError = { captured = it })
            runCurrent()

            // The corrupt-store read surfaces via onError instead of crashing the collector, and the
            // source degrades to null (resolver falls through) rather than dying silently.
            assertThat(captured).isInstanceOf(IOException::class.java)
            assertThat(source.snapshot(TestBool.key)).isNull()
        }

    @Test
    fun `collector recovers after a transient read failure`() = runTest(UnconfinedTestDispatcher()) {
        // data throws once (transient IO), then emits on resubscribe. A single failure must NOT
        // permanently kill the collector — otherwise every later override stays invisible until app
        // restart.
        val attempts = AtomicInteger(0)
        val store = object : DataStore<Preferences> {
            override val data = flow {
                if (attempts.getAndIncrement() == 0) throw IOException("transient")
                emit(preferencesOf(stringPreferencesKey(TestBool.key) to "true"))
            }
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences = throw UnsupportedOperationException()
        }

        var captured: Throwable? = null
        val source = PersistentOverrideSource(store, backgroundScope, onError = { captured = it })
        advanceTimeBy(5_000) // elapse the retry backoff so the collector resubscribes
        runCurrent()

        assertThat(captured).isInstanceOf(IOException::class.java)
        assertThat(attempts.get()).isAtLeast(2) // resubscribed after the transient failure
        assertThat(source.snapshot(TestBool.key)).isEqualTo("true")
    }

    @Test
    fun `primeSynchronously loads persisted overrides before first snapshot`() =
        runTest(UnconfinedTestDispatcher()) {
            // With priming, snapshot() must already see the persisted value WITHOUT advancing the
            // async collector — this is the cold-start path NetworkProvider hits when it resolves
            // ServerType synchronously while building Retrofit.
            val store = object : DataStore<Preferences> {
                override val data = flowOf(preferencesOf(stringPreferencesKey(TestBool.key) to "true"))
                override suspend fun updateData(
                    transform: suspend (Preferences) -> Preferences,
                ): Preferences = throw UnsupportedOperationException()
            }

            val source = PersistentOverrideSource(store, backgroundScope, primeSynchronously = true)

            // No runCurrent(): the value is loaded synchronously in the constructor.
            assertThat(source.snapshot(TestBool.key)).isEqualTo("true")
        }
}
