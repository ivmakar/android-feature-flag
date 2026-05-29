package io.github.ivmakar.featureflags.api

// Reports an internal, non-fatal failure (e.g. a corrupt or unreadable persistent-override store)
// to the consumer. The module logs nothing itself, so it carries no logging dependency; bind an
// implementation (e.g. backed by Timber) to observe these failures. When no handler is bound the
// failure is swallowed and the affected source degrades to "no opinion" (fall-through).
fun interface FeatureFlagsErrorHandler {
    fun onError(throwable: Throwable)
}
