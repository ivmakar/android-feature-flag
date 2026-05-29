package io.github.ivmakar.featureflags.fakes

import io.github.ivmakar.featureflags.api.Feature
import io.github.ivmakar.featureflags.api.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

// In-memory FeatureFlags for consumer and module tests. Each feature key gets a MutableStateFlow seeded
// with its default (or a builder-supplied override), so observe() is hot and set() drives
// re-emission without any DataStore or coroutine plumbing.
//
// Seed type-safely via the builder, which ties each value to its feature's type:
//   FakeFeatureFlags { put(FavoritesEnabled, true) }
// A bare FakeFeatureFlags() reports every flag's declared default.
class FakeFeatureFlags : FeatureFlags {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<Any>>()

    private fun <T : Any> flowOf(feature: Feature<T>): MutableStateFlow<Any> =
        flows.getOrPut(feature.key) { MutableStateFlow(feature.default) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(feature: Feature<T>): T = flowOf(feature).value as T

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> observe(feature: Feature<T>): Flow<T> =
        flowOf(feature).asStateFlow() as Flow<T>

    fun <T : Any> set(feature: Feature<T>, value: T) {
        flowOf(feature).value = value
    }

    class Builder {
        private val seed = mutableListOf<Pair<Feature<*>, Any>>()

        // value: T is bound to feature's type param, so a BoolFeature can only be seeded with a
        // Boolean, an EnumFeature<E> with an E, etc. — the mismatch the raw map constructor allowed
        // is now a compile error.
        fun <T : Any> put(feature: Feature<T>, value: T): Builder = apply { seed += feature to value }

        internal fun build(): FakeFeatureFlags = FakeFeatureFlags().also { fake ->
            seed.forEach { (feature, value) -> fake.flows[feature.key] = MutableStateFlow(value) }
        }
    }

    companion object {
        operator fun invoke(block: Builder.() -> Unit): FakeFeatureFlags =
            Builder().apply(block).build()
    }
}
