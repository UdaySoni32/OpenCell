package io.opencell.platform.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    // All platform services are @Singleton @Inject constructors
    // and are auto-discovered by Hilt through their @Inject annotations.
    // This module exists as a placeholder for any manual bindings needed later.
}
