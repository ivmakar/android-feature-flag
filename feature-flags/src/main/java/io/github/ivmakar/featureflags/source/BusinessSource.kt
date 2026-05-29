package io.github.ivmakar.featureflags.source

import io.github.ivmakar.featureflags.api.FeatureFlagSource
import io.github.ivmakar.featureflags.api.Priority
import io.github.ivmakar.featureflags.config.FeatureConfig

internal class BusinessSource(config: FeatureConfig) : FeatureFlagSource {
    override val priority = Priority.BUSINESS
    private val values = config.values
    override fun snapshot(key: String): String? = values[key]
}
