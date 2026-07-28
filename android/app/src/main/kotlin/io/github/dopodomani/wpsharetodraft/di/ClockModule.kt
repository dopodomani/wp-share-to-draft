package io.github.dopodomani.wpsharetodraft.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Injected rather than called statically, so [IntentParser][io.github.dopodomani.wpsharetodraft.presentation.share.IntentParser] tests can fix "now". */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}
