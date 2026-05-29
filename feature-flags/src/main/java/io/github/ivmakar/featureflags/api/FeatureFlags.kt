package io.github.ivmakar.featureflags.api

import kotlinx.coroutines.flow.Flow

interface FeatureFlags {
    fun <T : Any> get(feature: Feature<T>): T
    fun <T : Any> observe(feature: Feature<T>): Flow<T>
}
