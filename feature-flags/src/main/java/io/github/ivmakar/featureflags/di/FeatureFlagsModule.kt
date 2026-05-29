package io.github.ivmakar.featureflags.di

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ivmakar.featureflags.api.FeatureFlags
import io.github.ivmakar.featureflags.api.FeatureFlagsErrorHandler
import io.github.ivmakar.featureflags.config.BusinessFeatureConfig
import io.github.ivmakar.featureflags.config.DebugFeatureConfig
import io.github.ivmakar.featureflags.config.FeatureFlagsConfigurator
import io.github.ivmakar.featureflags.config.ForcedFeatureConfig
import io.github.ivmakar.featureflags.source.PersistentOverrideSource
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import java.util.Optional
import javax.inject.Singleton

private const val FEATURE_FLAGS_FILE = "feature_flags"

// Migrations contributed by the consumer (e.g. your :app module) via @IntoSet. They must NOT open a
// second DataStore on a file another active DataStore already owns: DataStore enforces one active
// instance per file per process and crashes the second reader. The module deliberately does not
// declare the migrations itself — only the consumer knows which legacy stores exist and already holds
// their live singletons to read/delete through.
@Module
@InstallIn(SingletonComponent::class)
interface FeatureFlagsMigrationsModule {

    // Empty default so consumers may contribute zero migrations without a missing-binding error.
    @Multibinds
    fun featureFlagsMigrations(): Set<DataMigration<Preferences>>

    // Optional: consumers may @Binds a FeatureFlagsErrorHandler to observe internal failures
    // (e.g. a corrupt override store). Absent by default — the failure is then swallowed and the
    // source degrades to fall-through.
    @BindsOptionalOf
    fun bindErrorHandler(): FeatureFlagsErrorHandler
}

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsModule {

    // Built via the factory (not a property-delegate) so the consumer-supplied migrations can be
    // injected. Uses a dedicated file (feature_flags), separate from any other DataStore the consumer
    // already owns — two active instances on one file crash at runtime.
    @Provides
    @Singleton
    @FeatureFlagsDataStore
    internal fun provideFeatureFlagsDataStore(
        @ApplicationContext context: Context,
        migrations: Set<@JvmSuppressWildcards DataMigration<Preferences>>,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = migrations.toList(),
        produceFile = { context.preferencesDataStoreFile(FEATURE_FLAGS_FILE) },
    )

    @Provides
    @Singleton
    fun providePersistentOverrideSource(
        @FeatureFlagsDataStore dataStore: DataStore<Preferences>,
        @IsDebug isDebug: Boolean,
        errorHandler: Optional<FeatureFlagsErrorHandler>,
    ): PersistentOverrideSource = PersistentOverrideSource(
        dataStore = dataStore,
        // Only debug registers this source in the resolver, so prime the cache only there: the cold-
        // start synchronous read (NetworkProvider ServerType) must see the persisted override, not
        // the build-variant default.
        primeSynchronously = isDebug,
        onError = { throwable -> errorHandler.ifPresent { it.onError(throwable) } },
    )

    @Provides
    @Singleton
    fun provideFeatureFlags(
        @IsDebug isDebug: Boolean,
        overrideSource: PersistentOverrideSource,
        business: BusinessFeatureConfig,
        debugOverrides: DebugFeatureConfig,
        forced: ForcedFeatureConfig,
    ): FeatureFlags = FeatureFlagsConfigurator(
        isDebug = isDebug,
        overrideSource = overrideSource.takeIf { isDebug },
        business = business.config,
        debugOverrides = debugOverrides.config,
        forced = forced.config,
    ).build()
}
