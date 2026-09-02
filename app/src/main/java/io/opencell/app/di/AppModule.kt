package io.opencell.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.opencell.app.call.AppCallUiDelegate
import io.opencell.platform.telecom.CallUiDelegate
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCallUiDelegate(impl: AppCallUiDelegate): CallUiDelegate
}
