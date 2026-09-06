package com.example.opencell.gateway.server

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import com.example.opencell.data.local.preferences.DeveloperPreferencesRepository
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.data.repository.MessageRepository
import com.example.opencell.domain.model.ApiKeyRecord
import com.example.opencell.domain.model.ServerLogType
import com.example.opencell.domain.model.WebhookRecord
import com.example.opencell.gateway.event.EventEngine
import com.example.opencell.gateway.logging.ServerLogRepository
import com.example.opencell.messaging.MessageEngine
import com.example.opencell.telecom.CallEngine
import com.example.opencell.ui.contacts.ContactStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class CallRequestDto(
    val phoneNumber: String,
    val contactName: String? = null
)

@Serializable
data class MessageRequestDto(
    val recipient: String,
    val body: String,
    val contactName: String? = null
)

@Serializable
data class WebhookRequestDto(
    val url: String,
    val events: List<String> = emptyList(),
    val secret: String? = null
)

@Serializable
data class ErrorResponseDto(
    val error: String,
    val message: String
)

@Serializable
data class HealthResponseDto(
    val status: String,
    val uptimeMs: Long,
    val activeWebsockets: Int,
    val version: String = "1.0.0"
)

@Serializable
data class DeviceResponseDto(
    val model: String,
    val manufacturer: String,
    val sdkVersion: Int,
    val batteryLevelPercent: Int,
    val isCharging: Boolean
)

@Serializable
data class CapabilitiesResponseDto(
    val telephonyCapable: Boolean,
    val smsCapable: Boolean,
    val callPermissionGranted: Boolean,
    val activeSimCount: Int
)

@Serializable
data class SimInfoResponseDto(
    val simState: String,
    val carrierName: String,
    val mccMnc: String,
    val countryIso: String,
    val activeSimCount: Int
)

@Serializable
data class NetworkInfoResponseDto(
    val networkType: String,
    val operatorName: String,
    val signalStrengthDbm: Int,
    val isConnected: Boolean
)

class GatewayServer(
    private val context: Context,
    private val developerPreferencesRepository: DeveloperPreferencesRepository,
    private val callRepository: CallRepository,
    private val messageRepository: MessageRepository,
    private val callEngine: CallEngine = CallEngine.instance ?: error("CallEngine not initialized"),
    private val messageEngine: MessageEngine = MessageEngine.instance ?: error("MessageEngine not initialized"),
    private val eventEngine: EventEngine = EventEngine.instance,
    private val serverLogRepository: ServerLogRepository = ServerLogRepository.instance,
    private val port: Int = 8080,
    private val host: String = "127.0.0.1"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var serverEngine: ApplicationEngine? = null
    private val startTimeMs = System.currentTimeMillis()
    private val activeWebsocketConnections = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (serverEngine != null) return

        serverEngine = embeddedServer(CIO, port = port, host = host) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(WebSockets)

            routing {
                // Health Check
                get("/v1/health") {
                    if (authenticateAndAuthorize(call, requiredScope = null) != null) {
                        call.respond(
                            HealthResponseDto(
                                status = "OK",
                                uptimeMs = System.currentTimeMillis() - startTimeMs,
                                activeWebsockets = activeWebsocketConnections.get()
                            )
                        )
                    }
                }

                // Device Info
                get("/v1/device") {
                    if (authenticateAndAuthorize(call, requiredScope = null) != null) {
                        val batteryManager = this@GatewayServer.context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                        val isCharging = batteryManager?.isCharging ?: false

                        call.respond(
                            DeviceResponseDto(
                                model = Build.MODEL ?: "Unknown",
                                manufacturer = Build.MANUFACTURER ?: "Unknown",
                                sdkVersion = Build.VERSION.SDK_INT,
                                batteryLevelPercent = batteryLevel,
                                isCharging = isCharging
                            )
                        )
                    }
                }

                // Capabilities Report
                get("/v1/capabilities") {
                    if (authenticateAndAuthorize(call, requiredScope = null) != null) {
                        val telephonyCapable = callEngine.isModemAvailable.value
                        val smsCapable = messageEngine.isSmsCapable.value
                        val telephonyManager = this@GatewayServer.context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        val activeSimCount = telephonyManager?.phoneCount ?: 1

                        call.respond(
                            CapabilitiesResponseDto(
                                telephonyCapable = telephonyCapable,
                                smsCapable = smsCapable,
                                callPermissionGranted = true,
                                activeSimCount = activeSimCount
                            )
                        )
                    }
                }

                // Calls API
                get("/v1/calls") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CALLS_READ) != null) {
                        val callsList = callRepository.getAllCalls().first()
                        call.respond(callsList)
                    }
                }

                post("/v1/calls") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CALLS_CREATE) != null) {
                        try {
                            val bodyText = call.receiveText()
                            val req = json.decodeFromString<CallRequestDto>(bodyText)
                            callEngine.initiateCall(
                                phoneNumber = req.phoneNumber,
                                contactName = req.contactName
                            )
                            eventEngine.emitCallCreated(
                                callId = UUID.randomUUID().toString(),
                                phoneNumber = req.phoneNumber,
                                type = "OUTGOING"
                            )
                            call.respond(
                                mapOf(
                                    "status" to "IN_PROGRESS",
                                    "phoneNumber" to req.phoneNumber,
                                    "message" to "Call initiation submitted"
                                )
                            )
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("BAD_REQUEST", e.message ?: "Invalid body"))
                        }
                    }
                }

                get("/v1/calls/{id}") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CALLS_READ) != null) {
                        val idParam = call.parameters["id"]
                        val allCalls = callRepository.getAllCalls().first()
                        val match = allCalls.find { it.id.toString() == idParam }
                        if (match != null) {
                            call.respond(match)
                        } else {
                            call.respond(HttpStatusCode.NotFound, ErrorResponseDto("NOT_FOUND", "Call with id $idParam not found"))
                        }
                    }
                }

                post("/v1/calls/{id}/answer") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CALLS_CONTROL) != null) {
                        val idParam = call.parameters["id"] ?: "0"
                        callEngine.answerCall()
                        eventEngine.emitCallStateChanged(
                            callId = idParam,
                            phoneNumber = callEngine.activeCallSession.value?.phoneNumber ?: "Unknown",
                            state = "ACTIVE"
                        )
                        call.respond(mapOf("status" to "SUCCESS", "message" to "Answer signal sent"))
                    }
                }

                post("/v1/calls/{id}/reject") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CALLS_CONTROL) != null) {
                        val idParam = call.parameters["id"] ?: "0"
                        val phone = callEngine.activeCallSession.value?.phoneNumber ?: "Unknown"
                        callEngine.rejectCall()
                        eventEngine.emitCallStateChanged(
                            callId = idParam,
                            phoneNumber = phone,
                            state = "REJECTED"
                        )
                        call.respond(mapOf("status" to "SUCCESS", "message" to "Reject signal sent"))
                    }
                }

                post("/v1/calls/{id}/hangup") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CALLS_CONTROL) != null) {
                        val idParam = call.parameters["id"] ?: "0"
                        val phone = callEngine.activeCallSession.value?.phoneNumber ?: "Unknown"
                        callEngine.hangupCall()
                        eventEngine.emitCallStateChanged(
                            callId = idParam,
                            phoneNumber = phone,
                            state = "ENDED"
                        )
                        call.respond(mapOf("status" to "SUCCESS", "message" to "Hangup signal sent"))
                    }
                }

                // Messages API
                get("/v1/messages") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_MESSAGES_READ) != null) {
                        val messages = messageRepository.getAllMessages().first()
                        call.respond(messages)
                    }
                }

                post("/v1/messages") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_MESSAGES_CREATE) != null) {
                        try {
                            val bodyText = call.receiveText()
                            val req = json.decodeFromString<MessageRequestDto>(bodyText)
                            messageEngine.sendMessage(
                                destination = req.recipient,
                                body = req.body,
                                contactName = req.contactName
                            )
                            eventEngine.emitMessageCreated(
                                messageId = UUID.randomUUID().toString(),
                                address = req.recipient,
                                body = req.body,
                                isIncoming = false
                            )
                            call.respond(
                                mapOf(
                                    "status" to "SUBMITTED",
                                    "recipient" to req.recipient,
                                    "message" to "SMS dispatch queued"
                                )
                            )
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("BAD_REQUEST", e.message ?: "Invalid body"))
                        }
                    }
                }

                get("/v1/messages/{id}") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_MESSAGES_READ) != null) {
                        val idParam = call.parameters["id"]?.toLongOrNull()
                        if (idParam != null) {
                            val msg = messageRepository.getMessageById(idParam)
                            if (msg != null) {
                                call.respond(msg)
                            } else {
                                call.respond(HttpStatusCode.NotFound, ErrorResponseDto("NOT_FOUND", "Message ID $idParam not found"))
                            }
                        } else {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("BAD_REQUEST", "Invalid message ID parameter"))
                        }
                    }
                }

                // Contacts API
                get("/v1/contacts") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_CONTACTS_READ) != null) {
                        // Room-backed shared store (waits briefly for first load
                        // on cold start so early requests aren't empty).
                        call.respond(ContactStore.contactsSnapshot())
                    }
                }

                // SIM API
                get("/v1/sim") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_SIM_READ) != null) {
                        val telephonyManager = this@GatewayServer.context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        val carrierName = telephonyManager?.simOperatorName?.ifBlank { "T-Mobile / OpenCell Mobile" } ?: "OpenCell Virtual Carrier"
                        val simState = when (telephonyManager?.simState) {
                            TelephonyManager.SIM_STATE_READY -> "READY"
                            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                            else -> "READY (SIMULATED)"
                        }
                        call.respond(
                            SimInfoResponseDto(
                                simState = simState,
                                carrierName = carrierName,
                                mccMnc = telephonyManager?.simOperator ?: "310260",
                                countryIso = telephonyManager?.simCountryIso ?: "us",
                                activeSimCount = telephonyManager?.phoneCount ?: 1
                            )
                        )
                    }
                }

                // Network API
                get("/v1/network") {
                    if (authenticateAndAuthorize(call, requiredScope = ApiKeyRecord.SCOPE_NETWORK_READ) != null) {
                        val telephonyManager = this@GatewayServer.context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        val operator = telephonyManager?.networkOperatorName?.ifBlank { "OpenCell LTE/5G" } ?: "OpenCell Cellular"
                        call.respond(
                            NetworkInfoResponseDto(
                                networkType = "5G_NR_SA",
                                operatorName = operator,
                                signalStrengthDbm = -85,
                                isConnected = true
                            )
                        )
                    }
                }

                // Webhooks API
                get("/v1/webhooks") {
                    if (authenticateAndAuthorize(call, requiredScope = null) != null) {
                        val webhooksList = developerPreferencesRepository.webhooks.first()
                        call.respond(webhooksList)
                    }
                }

                post("/v1/webhooks") {
                    if (authenticateAndAuthorize(call, requiredScope = null) != null) {
                        try {
                            val bodyText = call.receiveText()
                            val req = json.decodeFromString<WebhookRequestDto>(bodyText)
                            val secretKey = req.secret ?: "whsec_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                            val record = WebhookRecord(
                                id = UUID.randomUUID().toString(),
                                url = req.url,
                                events = req.events.ifEmpty { WebhookRecord.ALL_EVENTS },
                                secret = secretKey
                            )
                            developerPreferencesRepository.addWebhook(record)
                            call.respond(record)
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("BAD_REQUEST", e.message ?: "Invalid body"))
                        }
                    }
                }

                delete("/v1/webhooks/{id}") {
                    if (authenticateAndAuthorize(call, requiredScope = null) != null) {
                        val idParam = call.parameters["id"]
                        if (!idParam.isNull_or_blank()) {
                            developerPreferencesRepository.deleteWebhook(idParam!!)
                            call.respond(mapOf("status" to "DELETED", "id" to idParam))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("BAD_REQUEST", "Missing webhook ID"))
                        }
                    }
                }

                // WebSocket Events Streaming
                webSocket("/v1/events") {
                    val queryKey = call.request.queryParameters["api_key"]
                    val headerKey = call.request.headers["X-API-Key"]
                        ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
                    val activeKey = queryKey ?: headerKey

                    val keys = try {
                        developerPreferencesRepository.apiKeys.first()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val matchedKey = keys.find { it.key == activeKey }
                    if (matchedKey == null || (!matchedKey.scopes.contains(ApiKeyRecord.SCOPE_EVENTS_READ) && !matchedKey.scopes.contains("*"))) {
                        send(Frame.Text(json.encodeToString(ErrorResponseDto("UNAUTHORIZED", "Invalid API Key or missing events:read scope"))))
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }

                    activeWebsocketConnections.incrementAndGet()
                    serverLogRepository.log(ServerLogType.WEBSOCKET, "WebSocket Client Connected to /v1/events")

                    try {
                        send(
                            Frame.Text(
                                json.encodeToString(
                                    mapOf(
                                        "event" to "connection.established",
                                        "timestamp" to System.currentTimeMillis().toString(),
                                        "clientKey" to matchedKey.key.take(12) + "..."
                                    )
                                )
                            )
                        )

                        eventEngine.events.collect { telephonyEvent ->
                            val payloadJson = json.encodeToString(telephonyEvent)
                            send(Frame.Text(payloadJson))
                        }
                    } catch (e: Exception) {
                        serverLogRepository.log(ServerLogType.WEBSOCKET, "WebSocket Disconnected: ${e.message}")
                    } finally {
                        activeWebsocketConnections.decrementAndGet()
                    }
                }
            }
        }

        serverEngine?.start(wait = false)
        serverLogRepository.log(ServerLogType.SYSTEM, "Gateway Server Started on http://$host:$port")
    }

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        serverLogRepository.log(ServerLogType.SYSTEM, "Gateway Server Stopped")
    }

    private suspend fun authenticateAndAuthorize(
        call: ApplicationCall,
        requiredScope: String?
    ): ApiKeyRecord? {
        val apiKeyHeader = call.request.headers["X-API-Key"]
            ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()

        if (apiKeyHeader.isNull_or_blank()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto("UNAUTHORIZED", "Missing X-API-Key or Authorization Bearer header")
            )
            serverLogRepository.log(
                ServerLogType.REQUEST,
                "401 Unauthorized: ${call.request.httpMethod.value} ${call.request.uri} (Missing API Key)"
            )
            return null
        }

        val registeredKeys = try {
            developerPreferencesRepository.apiKeys.first()
        } catch (_: Exception) {
            emptyList()
        }

        val matchedKey = registeredKeys.find { it.key == apiKeyHeader }
        if (matchedKey == null || (!matchedKey.key.startsWith("oc_live_") && !matchedKey.key.startsWith("oc_test_"))) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto("UNAUTHORIZED", "Invalid API key or key format")
            )
            serverLogRepository.log(
                ServerLogType.REQUEST,
                "401 Unauthorized: ${call.request.httpMethod.value} ${call.request.uri} (Invalid key prefix/match)"
            )
            return null
        }

        if (requiredScope != null && !matchedKey.scopes.contains(requiredScope) && !matchedKey.scopes.contains("*")) {
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponseDto("FORBIDDEN", "API key missing required scope: $requiredScope")
            )
            serverLogRepository.log(
                ServerLogType.REQUEST,
                "403 Forbidden: ${call.request.httpMethod.value} ${call.request.uri} (Missing scope $requiredScope)"
            )
            return null
        }

        serverLogRepository.log(
            ServerLogType.REQUEST,
            "200 OK: ${call.request.httpMethod.value} ${call.request.uri} (Key: ${matchedKey.name})"
        )
        return matchedKey
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
