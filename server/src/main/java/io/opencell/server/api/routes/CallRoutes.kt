package io.opencell.server.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencell.core.model.*
import io.opencell.server.api.ApiRoutes
import io.opencell.server.api.ApiServerAttributes
import io.opencell.server.api.BadRequestException
import kotlinx.coroutines.flow.first

fun Route.callsRoutes(routes: ApiRoutes) {
    route("/calls") {

        // List calls — returns ALL calls from DB (history + active), with optional filtering
        get {
            val deviceId = call.parameters["device_id"]
            val state = call.parameters["state"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 100

            val calls = if (!deviceId.isNullOrBlank()) {
                routes.callEngine.getCallHistory().first().filter { it.deviceId == deviceId }
            } else {
                routes.callEngine.getCallHistory().first()
            }

            val filtered = if (!state.isNullOrBlank()) {
                calls.filter { it.state.equals(state, ignoreCase = true) }
            } else {
                calls
            }

            val limited = filtered.take(limit)

            call.respond(mapOf(
                "data" to limited.map { it.toApiMap() },
                "meta" to mapOf(
                    "total" to filtered.size,
                    "limit" to limit,
                    "returned" to limited.size
                )
            ))
        }

        // Initiate an outbound call
        post {
            val request = call.receive<MakeCallRequest>()
            if (request.to.isBlank()) {
                throw BadRequestException("'to' phone number is required")
            }
            val deviceId = routes.deviceEngine.getLocalDeviceId()
            val result = routes.callEngine.makeCall(
                phoneNumber = request.to,
                subscriptionId = request.subscription_id,
                deviceId = deviceId
            )
            result.fold(
                onSuccess = { c ->
                    call.respond(HttpStatusCode.Created, mapOf("data" to c.toApiMap()))
                },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf(
                            "code" to ErrorCodes.TELEPHONY_ERROR,
                            "message" to (e.message ?: "Failed to initiate call")
                        ))
                    )
                }
            )
        }

        // Get a specific call — searches DB, not just active calls
        get("/{call_id}") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val entity = routes.callEngine.getCallEntityById(callId)
            if (entity == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to mapOf("code" to "NOT_FOUND", "message" to "Call $callId not found"))
                )
            } else {
                call.respond(mapOf("data" to entity.toApiMap()))
            }
        }

        // Answer an incoming call
        post("/{call_id}/answer") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val result = routes.callEngine.answerCall(callId)
            result.fold(
                onSuccess = { c -> call.respond(mapOf("data" to c.toApiMap())) },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf("code" to ErrorCodes.TELEPHONY_ERROR, "message" to e.message))
                    )
                }
            )
        }

        // Reject an incoming call
        post("/{call_id}/reject") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val result = routes.callEngine.rejectCall(callId)
            result.fold(
                onSuccess = { call.respond(mapOf("data" to mapOf("id" to callId, "state" to "REJECTED"))) },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf("code" to ErrorCodes.TELEPHONY_ERROR, "message" to e.message))
                    )
                }
            )
        }

        // Hang up a call
        post("/{call_id}/hangup") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val result = routes.callEngine.hangupCall(callId)
            result.fold(
                onSuccess = { call.respond(mapOf("data" to mapOf("id" to callId, "state" to "ENDED"))) },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf("code" to ErrorCodes.TELEPHONY_ERROR, "message" to e.message))
                    )
                }
            )
        }

        // Hold a call
        post("/{call_id}/hold") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val result = routes.callEngine.holdCall(callId)
            result.fold(
                onSuccess = { c -> call.respond(mapOf("data" to c.toApiMap())) },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf("code" to ErrorCodes.TELEPHONY_ERROR, "message" to e.message))
                    )
                }
            )
        }

        // Resume a held call
        post("/{call_id}/resume") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val result = routes.callEngine.resumeCall(callId)
            result.fold(
                onSuccess = { c -> call.respond(mapOf("data" to c.toApiMap())) },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf("code" to ErrorCodes.TELEPHONY_ERROR, "message" to e.message))
                    )
                }
            )
        }
    }
}

private fun io.opencell.core.database.entity.CallEntity.toApiMap() = mapOf(
    "id" to id,
    "device_id" to deviceId,
    "subscription_id" to subscriptionId,
    "direction" to direction,
    "from" to fromNumber,
    "to" to toNumber,
    "state" to state,
    "started_at" to startedAt,
    "answered_at" to answeredAt,
    "ended_at" to endedAt,
    "duration_ms" to durationMs,
    "display_name" to displayName,
    "is_emergency" to isEmergency,
    "audio_state" to audioState
)

private fun io.opencell.core.model.Call.toApiMap() = mapOf(
    "id" to id,
    "device_id" to deviceId,
    "subscription_id" to subscriptionId,
    "direction" to direction.name,
    "from" to from,
    "to" to to,
    "state" to state.name,
    "started_at" to startedAt,
    "answered_at" to answeredAt,
    "ended_at" to endedAt,
    "duration_ms" to durationMs,
    "display_name" to displayName,
    "is_emergency" to isEmergency,
    "audio_state" to audioState.name
)
