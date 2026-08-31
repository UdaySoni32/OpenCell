package io.opencell.ui.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UiModule {
    // ViewModels are provided via @HiltViewModel annotation
    // and don't need manual bindings here.
}
