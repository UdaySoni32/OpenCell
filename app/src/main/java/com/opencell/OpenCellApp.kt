package com.opencell

import android.app.Application
import android.content.Context
import com.opencell.calllog.AppCallRecorder
import com.opencell.calllog.CallEntry
import com.opencell.calllog.CallbackLauncher
import com.opencell.calllog.SimCallLogSource
import com.opencell.calllog.mergeCallLogs
import com.opencell.calllog.notification.CallNotificationCache
import com.opencell.contacts.ContactsRepository
import com.opencell.data.OpenCellDatabase
import com.opencell.sms.SmsNotifier
import com.opencell.sms.SmsRepository
import com.opencell.telecom.CallManager
import com.opencell.telecom.CallPlacer
import com.opencell.telecom.IncomingCallNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class OpenCellApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual DI: one instance of each long-lived object. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: OpenCellDatabase by lazy { OpenCellDatabase.create(appContext) }
    val appCallRecorder: AppCallRecorder by lazy { AppCallRecorder(database.appCallDao()) }
    val callNotificationCache = CallNotificationCache()
    val callManager = CallManager()
    val callPlacer = CallPlacer(appContext)
    val incomingCallNotifier by lazy { IncomingCallNotifier(appContext) }
    val smsRepository = SmsRepository(appContext)
    val smsNotifier by lazy { SmsNotifier(appContext) }
    val contactsRepository = ContactsRepository(appContext.contentResolver)
    val callbackLauncher = CallbackLauncher(appContext, callNotificationCache, callPlacer)
    private val simCallLog = SimCallLogSource(appContext.contentResolver)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun unifiedCalls(): Flow<List<CallEntry>> =
        combine(simCallLog.observe(), database.appCallDao().observeAll().map { rows -> rows.map { it.toEntry() } }, ::mergeCallLogs)

    fun onIncomingSms(messageId: Long, from: String, body: String) {
        _events.tryEmit(AppEvent.SmsReceived(messageId, from, body, System.currentTimeMillis()))
    }
}
