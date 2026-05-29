package io.github.ivmakar.featureflags.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.ivmakar.featureflags.TestInt
import io.github.ivmakar.featureflags.config.FeatureConfig
import io.github.ivmakar.featureflags.config.FeatureFlagsConfigurator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Real-thread concurrency: runs writers and readers on Dispatchers.Default (not a single-threaded
// test dispatcher) so get() genuinely races the cache-refresh collector. The whole-snapshot swap in
// PersistentOverrideSource must mean a reader never observes a half-rebuilt cache.
//
// The business default is a SENTINEL (99) deliberately outside the writers' range (1..50), so every
// read is unambiguously classifiable: it is either the sentinel (cache not yet showing a write) or a
// genuinely-written value 1..50 — never 0, never a torn/partial value parsed from a half-written
// string. A loose `0..50` range (with default 0) could not distinguish a real write from a torn read.
class PersistentOverrideConcurrencyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `concurrent get and set never crash and converge to a written value`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("ff_concurrency.preferences_pb")
        }
        val source = PersistentOverrideSource(store, scope)
        val flags = FeatureFlagsConfigurator(
            isDebug = true,
            overrideSource = source,
            business = FeatureConfig { value(TestInt, SENTINEL_DEFAULT) },
        ).build()

        val validReads = (1..50).toSet() + SENTINEL_DEFAULT

        coroutineScope {
            val writers = (1..50).map { i ->
                launch(Dispatchers.Default) { source.set(TestInt.key, i.toString()) }
            }
            val readers = (1..200).map {
                async(Dispatchers.Default) { flags.get(TestInt) }
            }
            writers.forEach { it.join() }
            // No read throws, and every read is either the sentinel or a fully-written value — never a
            // torn intermediate (which would surface as a value outside this set).
            readers.awaitAll().forEach { assertThat(it).isIn(validReads) }
        }

        // Once the collector catches up to the committed writes, get() converges to a written value
        // (never stuck on the sentinel default).
        withTimeout(5_000) {
            while (flags.get(TestInt) == SENTINEL_DEFAULT) delay(10)
        }
        assertThat(flags.get(TestInt)).isIn(1..50)

        scope.cancel()
    }

    private companion object {
        const val SENTINEL_DEFAULT = 99
    }
}
