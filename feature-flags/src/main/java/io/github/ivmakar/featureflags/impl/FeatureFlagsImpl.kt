package io.github.ivmakar.featureflags.impl

import io.github.ivmakar.featureflags.api.Feature
import io.github.ivmakar.featureflags.api.FeatureFlagSource
import io.github.ivmakar.featureflags.api.FeatureFlags
import io.github.ivmakar.featureflags.codec.FeatureCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

internal class FeatureFlagsImpl(
    sources: List<FeatureFlagSource>,
) : FeatureFlags {

    // Sorted by descending rank; sortedByDescending is stable, so equal-rank sources keep their
    // input order (first-in-list wins on ties, per Priority's contract). The source list is fixed
    // at construction — to change sources, build a new resolver.
    private val orderedSources: List<FeatureFlagSource> =
        sources.sortedByDescending { it.priority.rank }

    private fun <T : Any> resolve(feature: Feature<T>): T {
        for (source in orderedSources) {
            val raw = source.snapshot(feature.key) ?: continue
            val decoded = FeatureCodec.decode(feature, raw)
            if (decoded != null) return decoded
        }
        return feature.default
    }

    override fun <T : Any> get(feature: Feature<T>): T = resolve(feature)

    override fun <T : Any> observe(feature: Feature<T>): Flow<T> {
        // Merge every reactive source's change stream; non-reactive sources return null from
        // changes() and are dropped. onStart guarantees an initial emission even when no source is
        // reactive (e.g. release builds without PersistentOverrideSource).
        val triggers = orderedSources.mapNotNull { it.changes() }
        val changeStream = if (triggers.isEmpty()) emptyFlow() else triggers.merge()
        return changeStream
            .onStart { emit(Unit) }
            .map { resolve(feature) }
            .distinctUntilChanged()
    }
}
