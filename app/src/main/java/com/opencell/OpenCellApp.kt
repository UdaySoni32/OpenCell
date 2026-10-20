package com.opencell

import android.app.Application
import android.content.Context
import android.util.Log
import com.opencell.agent.Assistant
import com.opencell.agent.AssistantNotifications
import com.opencell.assistant.AssistantOrchestrator
import com.opencell.ai.AiSettingsStore
import com.opencell.ai.KeystoreSecrets
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
import com.opencell.telecom.CallState
import com.opencell.telecom.CallPlacer
import com.opencell.telecom.IncomingCallNotifier
import com.opencell.voice.VoiceController
import com.opencell.voice.LocalVoiceController
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
    val appContext = context.applicationContext
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
                override fun placeCall(to: String, goal: String?): Boolean =
                    if (goal == null) callPlacer.placeCall(to)
                    else voiceController.placeCall(goal) { token -> callPlacer.placeCall(to, token) }
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

    val secrets = KeystoreSecrets(appContext)
    val aiSettings = AiSettingsStore(appContext, secrets)
    val assistantOrchestrator by lazy { AssistantOrchestrator(aiSettings, database.memoryDao()) }

    val localVoiceController by lazy { LocalVoiceController(appContext, appScope, aiSettings) }

    val assistant: Assistant by lazy {
        Assistant(
            ai = aiSettings,
            llmFactory = aiSettings::buildLlm,
            sms = smsRepository,
            contacts = contactsRepository,
            memory = database.memoryDao(),
            executor = actionExecutor,
            policy = settings::currentPolicy,
            notifier = AssistantNotifications(appContext),
        )
    }

    val voiceController: VoiceController by lazy {
        VoiceController(
            context = appContext,
            scope = appScope,
            ai = aiSettings,
            calls = callManager,
            executor = actionExecutor,
            contacts = contactsRepository,
            memory = database.memoryDao(),
            tasks = database.assistantTaskDao(),
            notifier = AssistantNotifications(appContext),
        )
    }

    init {
        appScope.launch { publishCallEvents() }
    }

    // shortcut: assistant work runs in the app process after the SMS broadcast finishes; Android may kill a cached
    // process during a slow LLM call. Move to WorkManager / a short foreground service if replies go missing.
    private fun launchAssistant(what: String, block: suspend Assistant.() -> Unit) {
        appScope.launch { runCatching { assistant.block() }.onFailure { Log.e("Assistant", "assistant failed on $what", it) } }
    }

    fun unifiedCalls(): Flow<List<CallEntry>> =
        combine(simCallLog.observe(), database.appCallDao().observeAll().map { rows -> rows.map { it.toEntry() } }, ::mergeCallLogs)

    fun onIncomingSms(messageId: Long, from: String, body: String) {
        _events.tryEmit(AppEvent.SmsReceived(messageId, from, body, System.currentTimeMillis()))
        launchAssistant("sms") { onIncomingSms(from, body) }
    }

    /** Emits one event per call state change, plus "ended" when a call disappears. */
    private suspend fun publishCallEvents() {
        var previous = emptyMap<String, ActiveCall>()
        callManager.activeCalls.collect { calls ->
            val now = System.currentTimeMillis()
            val current = calls.associateBy { it.id }
            current.values.filter { previous[it.id]?.state != it.state }.forEach {
                _events.tryEmit(AppEvent.CallStateChanged(it.id, it.number, it.state.name.lowercase(), it.isIncoming, now))
                if (it.isIncoming && it.state == CallState.RINGING) launchAssistant("call") { onRingingCall(it.id, it.number) }
                voiceController.onCallState(it.id, it.number, it.state, it.isIncoming)
            }
            (previous.keys - current.keys).forEach { id ->
                val gone = previous.getValue(id)
                _events.tryEmit(AppEvent.CallStateChanged(id, gone.number, "ended", gone.isIncoming, now))
            }
            previous = current
        }
    }
}
