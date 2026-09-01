package io.opencell.server.api

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.opencell.core.model.ApiError
import io.opencell.core.model.ApiErrorResponse
import io.opencell.core.model.ErrorCodes
import io.opencell.core.crypto.CryptoUtils
import io.opencell.server.auth.AuthenticationService
import io.opencell.server.api.routes.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded API Server running inside the Android application.
 */
class ApiServer(
    private val port: Int = 8900,
    private val host: String = "0.0.0.0",
    private val authService: AuthenticationService,
    private val routes: ApiRoutes
) {
    private var server: ApplicationEngine? = null
    private val wsClients = ConcurrentHashMap<String, WebSocketSession>()

    fun start() {
        server = embeddedServer(Netty, port = port, host = host) {
            configureSerialization()
            configureCors()
            configureStatusPages()
            configureWebSockets()
            configureRouting(authService, routes, wsClients)
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 5000)
        server = null
        wsClients.clear()
    }

    fun isRunning(): Boolean = server != null

    private fun Application.configureSerialization() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    private fun Application.configureCors() {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Accept)
            allowHeader("X-Request-ID")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
        }
    }

    private fun Application.configureStatusPages() {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val requestId = CryptoUtils.generateRequestId()
                when (cause) {
                    is AuthenticationException -> call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiErrorResponse(ApiError(
                            code = ErrorCodes.AUTHENTICATION_ERROR,
                            message = cause.message ?: "Authentication failed",
                            request_id = requestId
                        ))
                    )
                    is AuthorizationException -> call.respond(
                        HttpStatusCode.Forbidden,
                        ApiErrorResponse(ApiError(
                            code = ErrorCodes.AUTHORIZATION_ERROR,
                            message = cause.message ?: "Authorization failed",
                            request_id = requestId
                        ))
                    )
                    is BadRequestException -> call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse(ApiError(
                            code = ErrorCodes.INVALID_REQUEST,
                            message = cause.message ?: "Invalid request",
                            request_id = requestId
                        ))
                    )
                    else -> call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorResponse(ApiError(
                            code = ErrorCodes.INTERNAL_ERROR,
                            message = cause.message ?: "Internal server error",
                            request_id = requestId
                        ))
                    )
                }
            }
        }
    }

    private fun Application.configureWebSockets() {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(60)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
    }

    private fun Application.configureRouting(
        authService: AuthenticationService,
        routes: ApiRoutes,
        wsClients: ConcurrentHashMap<String, WebSocketSession>
    ) {
        routing {
            get("/health") {
                call.respond(mapOf(
                    "status" to "ok",
                    "version" to "0.1.0-mvp",
                    "timestamp" to System.currentTimeMillis()
                ))
            }

            route("/v1") {
                install(createRouteScopedPlugin("Authentication") {
                    onCall { call ->
                        val requestId = call.request.headers["X-Request-ID"] ?: CryptoUtils.generateRequestId()
                        val authHeader = call.request.headers[HttpHeaders.Authorization]
                        val apiKey = authService.extractApiKey(authHeader)

                        if (apiKey == null) {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ApiErrorResponse(ApiError(
                                    code = ErrorCodes.AUTHENTICATION_ERROR,
                                    message = "Missing or invalid Authorization header.",
                                    request_id = requestId
                                ))
                            )
                            return@onCall
                        }

                        val result = authService.authenticate(apiKey)
                        if (!result.authenticated) {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ApiErrorResponse(ApiError(
                                    code = ErrorCodes.AUTHENTICATION_ERROR,
                                    message = result.error ?: "Authentication failed",
                                    request_id = requestId
                                ))
                            )
                            return@onCall
                        }

                        call.attributes.put(ApiServerAttributes.apiKeyId, result.apiKeyId!!)
                        call.attributes.put(ApiServerAttributes.projectId, result.projectId!!)
                        call.attributes.put(ApiServerAttributes.scopes, result.scopes)
                        call.attributes.put(ApiServerAttributes.requestId, requestId)
                    }
                })

                devicesRoutes(routes)
                callsRoutes(routes)
                messagesRoutes(routes)
                contactsRoutes(routes)
                ussdRoutes(routes)
                eventsRoutes(routes, wsClients)
                webhooksRoutes(routes)
                projectsRoutes(routes)
                apiKeyRoutes(routes)
                auditLogRoutes(routes)
                networkRoutes(routes)
                simRoutes(routes)
                capabilitiesRoutes(routes)
            }

            // WebSocket endpoint with authentication via query param
            webSocket("/v1/events/stream") {
                // Authenticate via query param: ?api_key=oc_xxxxx
                val apiKey = call.request.queryParameters["api_key"]
                if (apiKey == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing api_key query parameter"))
                    return@webSocket
                }

                val authResult = authService.authenticate(apiKey)
                if (!authResult.authenticated) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication failed: ${authResult.error}"))
                    return@webSocket
                }

                val sessionId = CryptoUtils.generateId("ws")
                wsClients[sessionId] = this
                try {
                    // Send connected confirmation
                    send(Frame.Text(buildJsonObject {
                        put("type", JsonPrimitive("connected"))
                        put("session_id", JsonPrimitive(sessionId))
                        put("message", JsonPrimitive("Connected to OpenCell event stream"))
                    }.toString()))

                    // Keep connection alive and listen for client messages
                    for (frame in incoming) {
                        // Client can send ping/pong or commands
                        if (frame is Frame.Text) {
                            // Handle client messages if needed
                        }
                    }
                } finally {
                    wsClients.remove(sessionId)
                }
            }
        }
    }

    /**
     * Broadcast an event to all connected WebSocket clients.
     * Called by EventEngine when events are emitted.
     */
    suspend fun broadcastEvent(eventName: String, deviceId: String, data: Map<String, Any>) {
        if (wsClients.isEmpty()) return

        val payload = buildJsonObject {
            put("type", JsonPrimitive("event"))
            put("event", JsonPrimitive(eventName))
            put("device_id", JsonPrimitive(deviceId))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
            data.forEach { (k, v) ->
                put(k, when (v) {
                    is String -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                })
            }
        }.toString()

        val frame = Frame.Text(payload)
        val deadClients = mutableListOf<String>()

        wsClients.forEach { (sessionId, session) ->
            try {
                session.send(frame)
            } catch (e: Exception) {
                deadClients.add(sessionId)
            }
        }

        // Clean up dead connections
        deadClients.forEach { wsClients.remove(it) }
    }
}

object ApiServerAttributes {
    val apiKeyId = io.ktor.util.AttributeKey<String>("apiKeyId")
    val projectId = io.ktor.util.AttributeKey<String>("projectId")
    val scopes = io.ktor.util.AttributeKey<List<String>>("scopes")
    val requestId = io.ktor.util.AttributeKey<String>("requestId")
}

class AuthenticationException(message: String) : RuntimeException(message)
class AuthorizationException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
