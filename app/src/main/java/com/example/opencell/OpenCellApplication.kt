package com.example.opencell

import android.app.Application
import com.example.opencell.data.local.db.AppDatabase
import com.example.opencell.data.local.preferences.DeveloperPreferencesRepository
import com.example.opencell.data.local.preferences.DeveloperPreferencesRepositoryImpl
import com.example.opencell.data.local.preferences.SettingsDataStore
import com.example.opencell.data.local.preferences.SettingsDataStoreImpl
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.data.repository.CallRepositoryImpl
import com.example.opencell.data.repository.ContactRepository
import com.example.opencell.data.repository.ContactRepositoryImpl
import com.example.opencell.data.repository.MessageRepository
import com.example.opencell.data.repository.MessageRepositoryImpl
import com.example.opencell.messaging.MessageEngine
import com.example.opencell.messaging.SmsAdapter
import com.example.opencell.telecom.CallEngine
import com.example.opencell.telecom.TelecomAdapter
import com.example.opencell.ui.contacts.ContactStore

class OpenCellApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val developerPreferencesRepository: DeveloperPreferencesRepository by lazy { DeveloperPreferencesRepositoryImpl(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStoreImpl(this) }
    val callRepository: CallRepository by lazy { CallRepositoryImpl(database.callRecordDao()) }
    val messageRepository: MessageRepository by lazy { MessageRepositoryImpl(database.messageRecordDao()) }
    val contactRepository: ContactRepository by lazy { ContactRepositoryImpl(database.contactDao()) }

    val telecomAdapter by lazy { TelecomAdapter(this) }
    val callEngine by lazy { CallEngine(telecomAdapter, callRepository) }

    val smsAdapter by lazy { SmsAdapter(this) }
    val messageEngine by lazy { MessageEngine(this, smsAdapter, messageRepository) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Load contacts from Room into the shared store (seeds defaults on
        // first launch). UI and the gateway API both read from here.
        ContactStore.init(contactRepository)
    }

    companion object {
        lateinit var instance: OpenCellApplication
            private set

        /**
         * Null-safe accessor so engine code can avoid touching [instance]
         * when running in environments without an Application (e.g. JVM unit tests).
         */
        val instanceOrNull: OpenCellApplication?
            get() = if (::instance.isInitialized) instance else null
    }
}
