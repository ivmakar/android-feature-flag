package io.github.ivmakar.featureflags.config

import io.github.ivmakar.featureflags.api.BoolFeature
import io.github.ivmakar.featureflags.api.Feature
import io.github.ivmakar.featureflags.codec.FeatureCodec

class FeatureConfigBuilder {
    // LinkedHashMap so a later put for the same key overwrites the earlier one: last-wins.
    private val raw = LinkedHashMap<String, String>()

    fun enable(feature: BoolFeature) = value(feature, true)

    fun disable(feature: BoolFeature) = value(feature, false)

    fun <T : Any> value(feature: Feature<T>, value: T) {
        raw[feature.key] = FeatureCodec.encode(feature, value)
    }

    fun build(): FeatureConfig = FeatureConfig.of(raw.toMap())
}
