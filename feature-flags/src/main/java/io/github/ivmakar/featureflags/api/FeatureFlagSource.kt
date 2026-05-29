package io.github.ivmakar.featureflags.api

import kotlinx.coroutines.flow.Flow

interface FeatureFlagSource {
    val priority: Priority

    // Synchronous read. null means "this source has no opinion about the key" — the resolver
    // falls through to the next source rather than treating it as a value.
    fun snapshot(key: String): String?

    // null means the source never changes at runtime, so observe() does not subscribe to it.
    fun changes(): Flow<Unit>? = null
}
