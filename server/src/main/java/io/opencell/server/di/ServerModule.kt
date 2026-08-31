package io.opencell.server.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.opencell.server.api.ApiRoutes
import io.opencell.server.api.ApiServer
import io.opencell.server.auth.AuthenticationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {

    @Provides
    @Singleton
    fun provideApiServer(
        authService: AuthenticationService,
        routes: ApiRoutes
    ): ApiServer {
        return ApiServer(
            port = 8900,
            // 0.0.0.0 binds on all interfaces — allows access from devices on the same WiFi network
            host = "0.0.0.0",
            authService = authService,
            routes = routes
        )
    }
    // ApiRoutes is @Singleton @Inject constructor — Hilt provides it automatically.
}
