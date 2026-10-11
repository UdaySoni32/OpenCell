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
import com.opencell.data.SettingsStore
import com.opencell.gateway.PairingManager
import com.opencell.policy.ActionExecutor
import com.opencell.policy.ActionHandlers
import com.opencell.policy.ActionType
import com.opencell.policy.ApprovalNotifier
import com.opencell.sms.SmsNotifier
import com.opencell.sms.SmsRepository
import com.opencell.telecom.ActiveCall
import com.opencell.telecom.CallManager
import com.opencell.telecom.CallPlacer
import com.opencell.telecom.IncomingCallNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: OpenCellDatabase by lazy { OpenCellDatabase.create(appContext) }
    val settings = SettingsStore(appContext)
    val appCallRecorder: AppCallRecorder by lazy { AppCallRecorder(database.appCallDao()) }
    val callNotificationCache = CallNotificationCache()
    val callManager = CallManager()
    val callPlacer = CallPlacer(appContext)
    val incomingCallNotifier by lazy { IncomingCallNotifier(appContext) }
    val smsRepository = SmsRepository(appContext)
    val smsNotifier by lazy { SmsNotifier(appContext) }
    val contactsRepository = ContactsRepository(appContext.contentResolver)
    val callbackLauncher = CallbackLauncher(appContext, callNotificationCache, callPlacer)
    val pairingManager: PairingManager by lazy { PairingManager(database.pairedDeviceDao()) }
    private val simCallLog = SimCallLogSource(appContext.contentResolver)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    val actionExecutor: ActionExecutor by lazy {
        ActionExecutor(
            dao = database.policyDao(),
            policySettings = settings::currentPolicy,
            handlers = object : ActionHandlers {
                override suspend fun sendSms(to: String, body: String) = smsRepository.send(to, body)
                override fun placeCall(to: String) = callPlacer.placeCall(to)
                override fun controlCall(type: ActionType, callId: String): Boolean {
                    if (callManager.activeCalls.value.none { it.id == callId }) return false
                    when (type) {
                        ActionType.ANSWER_CALL -> callManager.answer(callId)
                        ActionType.REJECT_CALL -> callManager.reject(callId)
                        else -> callManager.hangup(callId)
                    }
                    return true
                }
            },
            prompter = ApprovalNotifier(appContext),
            onApprovalChanged = {
                _events.tryEmit(AppEvent.ApprovalChanged(it.id, it.status.name.lowercase(), it.action.name.lowercase(), it.target, System.currentTimeMillis()))
            },
        )
    }

    init {
        appScope.launch { publishCallEvents() }
    }

    fun unifiedCalls(): Flow<List<CallEntry>> =
        combine(simCallLog.observe(), database.appCallDao().observeAll().map { rows -> rows.map { it.toEntry() } }, ::mergeCallLogs)

    fun onIncomingSms(messageId: Long, from: String, body: String) {
        _events.tryEmit(AppEvent.SmsReceived(messageId, from, body, System.currentTimeMillis()))
    }

    /** Emits one event per call state change, plus "ended" when a call disappears. */
    private suspend fun publishCallEvents() {
        var previous = emptyMap<String, ActiveCall>()
        callManager.activeCalls.collect { calls ->
            val now = System.currentTimeMillis()
            val current = calls.associateBy { it.id }
            current.values.filter { previous[it.id]?.state != it.state }.forEach {
                _events.tryEmit(AppEvent.CallStateChanged(it.id, it.number, it.state.name.lowercase(), it.isIncoming, now))
            }
            (previous.keys - current.keys).forEach { id ->
                val gone = previous.getValue(id)
                _events.tryEmit(AppEvent.CallStateChanged(id, gone.number, "ended", gone.isIncoming, now))
            }
            previous = current
        }
    }
}
