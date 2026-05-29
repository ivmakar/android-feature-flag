package io.github.ivmakar.featureflags.di

import javax.inject.Qualifier

// The consumer binds its BuildConfig.DEBUG under this qualifier; the module stays build-variant
// agnostic.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsDebug

// Distinguishes this module's feature_flags DataStore from any other DataStore<Preferences> bound in
// the merged SingletonComponent, so the binding never collides with a consumer's own store.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeatureFlagsDataStore
