package io.github.ivmakar.featureflags

import io.github.ivmakar.featureflags.api.FeatureFlagSource
import io.github.ivmakar.featureflags.api.Priority
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeSource(
    override val priority: Priority,
    private val values: Map<String, String> = emptyMap(),
) : FeatureFlagSource {
    override fun snapshot(key: String): String? = values[key]
}

// Reactive source for observe() tests: put/clear mutate the snapshot and pulse changes() the same
// way PersistentOverrideSource does, so the resolver re-resolves.
internal class ReactiveFakeSource(
    override val priority: Priority,
) : FeatureFlagSource {

    private val values = HashMap<String, String>()
    private val trigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(Unit) }

    override fun snapshot(key: String): String? = values[key]

    override fun changes(): Flow<Unit> = trigger

    fun put(key: String, raw: String) {
        values[key] = raw
        trigger.tryEmit(Unit)
    }

    fun clear(key: String) {
        values.remove(key)
        trigger.tryEmit(Unit)
    }
}
