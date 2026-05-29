package io.github.ivmakar.featureflags.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ivmakar.featureflags.api.Feature
import io.github.ivmakar.featureflags.api.FeatureFlagSource
import io.github.ivmakar.featureflags.api.Priority
import io.github.ivmakar.featureflags.codec.FeatureCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// DataStore-backed override store for QA / debug builds.
//
// snapshot(key) must be synchronous (the resolver's get() path is sync and runs on init paths
// such as NetworkProvider), but DataStore reads are suspending. The bridge is an in-memory cache
// fed by a single long-lived collector started in the constructor on a SupervisorJob scope:
//   - Until the first emission lands, the cache is empty and snapshot() returns null. The resolver
//     then falls through to lower-priority sources (BuildVariant → Business → default). A not-yet-
//     loaded debug override must never beat the business default, so this is the intended behaviour.
//   - set/remove write to DataStore; the collector observes the change, refreshes the cache, and
//     pulses `changes`, which drives observe().
//   - If a DataStore read fails (corrupt / unreadable store), the collector reports it via onError
//     and then retries after a backoff (retryWhen) instead of dying. A transient IO error must not
//     permanently freeze the cache: were the collector to stop, every later override write would
//     stay invisible until the app restarts. On a persistently unreadable store the cache keeps its
//     last good contents and snapshot() degrades to fall-through rather than crashing the resolver.
//     The module logs nothing itself — consumers wire onError to their own logger — so it carries no
//     logging dependency.
class PersistentOverrideSource(
    private val dataStore: DataStore<Preferences>,
    // Default scope keeps DI simple; tests inject a controllable scope. IO dispatcher matches the
    // disk-backed nature of DataStore.
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    // When true, the cache is loaded synchronously in the constructor before any get()/observe() can
    // resolve, so the first synchronous snapshot() (e.g. resolving a server endpoint while building a
    // network client at startup) sees the persisted override instead of falling through to the
    // build-variant default. Register this source only in debug builds and pass isDebug here — the
    // blocking disk read then never happens in release.
    primeSynchronously: Boolean = false,
    // Reports a DataStore read failure to the consumer. Default no-op keeps the module dependency-
    // free; the consumer can wire this to a logger (e.g. Timber).
    private val onError: (Throwable) -> Unit = {},
) : FeatureFlagSource {

    override val priority = Priority.LOCAL_OVERRIDES

    // Whole-snapshot swap (not a mutable map): a reader always sees a complete, consistent set of
    // overrides — either the old one or the new one — never a half-rebuilt map. @Volatile publishes
    // the new reference to the synchronous snapshot() readers on other threads.
    @Volatile
    private var cache: Map<String, String> = emptyMap()

    // One hot trigger shared by every observe() subscriber, pulsed by the single collector below.
    // Conflated (DROP_OLDEST + replay 1): it is a "something changed" signal, not a value stream —
    // the resolver re-reads the cache on each pulse and distinctUntilChanged dedups. tryEmit never
    // suspends, so a slow observer can't stall the cache-refresh collector.
    private val changes = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        if (primeSynchronously) {
            // Best-effort synchronous warm-up. A failure here is non-fatal: the async collector below
            // reports it via onError and retries, and snapshot() degrades to fall-through meanwhile.
            try {
                cache = runBlocking { dataStore.data.first() }.toCache()
            } catch (t: Throwable) {
                onError(t)
            }
        }
        scope.launch {
            dataStore.data
                .retryWhen { cause, _ ->
                    onError(cause)
                    delay(RETRY_BACKOFF_MS)
                    true
                }
                .collect { prefs ->
                    cache = prefs.toCache()
                    changes.tryEmit(Unit)
                }
        }
    }

    private fun Preferences.toCache(): Map<String, String> =
        asMap().entries.associate { (k, v) -> k.name to v.toString() }

    override fun snapshot(key: String): String? = cache[key]

    override fun changes(): Flow<Unit> = changes.asSharedFlow()

    // Raw write bypassing the codec — internal so callers must go through the typed overload below.
    internal suspend fun set(key: String, raw: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = raw }
    }

    // Typed write: encodes through the same codec the resolver decodes with, so callers store
    // overrides without hand-serialising bool/enum/int values.
    suspend fun <T : Any> set(feature: Feature<T>, value: T) =
        set(feature.key, FeatureCodec.encode(feature, value))

    suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    private companion object {
        // Backoff before the collector resubscribes after a DataStore read failure. Long enough that
        // a persistently-corrupt store doesn't busy-loop onError; short enough that a transient error
        // recovers quickly. Debug-only store, so the exact value is not load-bearing.
        const val RETRY_BACKOFF_MS = 3_000L
    }
}
