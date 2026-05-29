package io.github.ivmakar.featureflags.config

// Immutable map of declared raw values keyed by Feature.key. Built via FeatureConfigBuilder.
// `values` is internal: the codec-serialised wire format is an implementation detail the sources
// read, not part of the public config API (consumers build configs through the typed DSL).
class FeatureConfig private constructor(
    internal val values: Map<String, String>,
) {
    companion object {
        fun empty(): FeatureConfig = FeatureConfig(emptyMap())

        internal fun of(values: Map<String, String>): FeatureConfig = FeatureConfig(values)
    }
}

inline fun FeatureConfig(block: FeatureConfigBuilder.() -> Unit): FeatureConfig =
    FeatureConfigBuilder().apply(block).build()
