package io.github.ivmakar.featureflags.config

import io.github.ivmakar.featureflags.api.FeatureFlags
import io.github.ivmakar.featureflags.impl.FeatureFlagsImpl
import io.github.ivmakar.featureflags.source.BusinessSource
import io.github.ivmakar.featureflags.source.BuildVariantSource
import io.github.ivmakar.featureflags.source.ForcedSource
import io.github.ivmakar.featureflags.source.PersistentOverrideSource

class FeatureFlagsConfigurator(
    private val isDebug: Boolean,
    private val overrideSource: PersistentOverrideSource?,
    private val business: FeatureConfig,
    private val debugOverrides: FeatureConfig = FeatureConfig.empty(),
    private val forced: FeatureConfig = FeatureConfig.empty(),
) {
    // BuildVariantSource and PersistentOverrideSource exist only in debug builds: a release build
    // must never let a build-variant override or a QA-set persistent override shadow the business
    // default. ForcedSource always wins when populated (kill-switch / test escape hatch).
    fun build(): FeatureFlags {
        val sources = buildList {
            add(BusinessSource(business))
            if (isDebug) {
                add(BuildVariantSource(debugOverrides))
                overrideSource?.let(::add)
            }
            add(ForcedSource(forced))
        }
        return FeatureFlagsImpl(sources)
    }
}
