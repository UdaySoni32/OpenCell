package io.opencell.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.opencell.core.database.OpenCellDatabase
import io.opencell.core.database.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenCellDatabase {
        return Room.databaseBuilder(
            context,
            OpenCellDatabase::class.java,
            "opencell.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDeviceDao(db: OpenCellDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideCallDao(db: OpenCellDatabase): CallDao = db.callDao()

    @Provides
    fun provideMessageDao(db: OpenCellDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideApiKeyDao(db: OpenCellDatabase): ApiKeyDao = db.apiKeyDao()

    @Provides
    fun provideWebhookDao(db: OpenCellDatabase): WebhookDao = db.webhookDao()

    @Provides
    fun provideEventDao(db: OpenCellDatabase): EventDao = db.eventDao()

    @Provides
    fun provideAuditLogDao(db: OpenCellDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    fun provideConfigurationDao(db: OpenCellDatabase): ConfigurationDao = db.configurationDao()

    @Provides
    fun provideCommandDao(db: OpenCellDatabase): CommandDao = db.commandDao()
}
