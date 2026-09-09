package com.example.opencell

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    var isAppInForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isAppInForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    isAppInForeground = false
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Load contacts from system ContactsContract / Room into the shared store
        ContactStore.init(contactRepository, this)
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
