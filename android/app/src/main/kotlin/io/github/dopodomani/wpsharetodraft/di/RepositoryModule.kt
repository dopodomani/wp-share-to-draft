package io.github.dopodomani.wpsharetodraft.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dopodomani.wpsharetodraft.data.WordPressDestination
import io.github.dopodomani.wpsharetodraft.data.local.AndroidLogger
import io.github.dopodomani.wpsharetodraft.data.local.EncryptedSettingsRepository
import io.github.dopodomani.wpsharetodraft.domain.Destination
import io.github.dopodomani.wpsharetodraft.domain.Logger
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import javax.inject.Singleton

/**
 * `@Binds`, not `@Provides` -- neither implementation needs custom construction logic
 * beyond its own `@Inject constructor`. See docs/phase3-android-app-design.md#6-hilt-di.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDestination(impl: WordPressDestination): Destination

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: EncryptedSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidLogger): Logger
}
