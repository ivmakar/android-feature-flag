package io.github.ivmakar.featureflags.config

// Thin typed wrappers so the consumer can @Provides each config independently and the module's Hilt
// module can consume them by distinct type without a circular dependency on flag declarations.
// (Only needed for the Hilt path; manual wiring passes FeatureConfig straight to the configurator.)
class BusinessFeatureConfig(val config: FeatureConfig)

class DebugFeatureConfig(val config: FeatureConfig)

class ForcedFeatureConfig(val config: FeatureConfig)
