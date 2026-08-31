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

fun Route.callsRoutes(routes: ApiRoutes) {
    route("/calls") {

        // List calls (active and recent)
        get {
            val deviceId = routes.deviceEngine.getLocalDeviceId()
            val activeCalls = routes.callEngine.activeCalls.value
            val allCallModels = activeCalls.map { call -> call.toApiMap() }
            call.respond(mapOf(
                "data" to allCallModels,
                "meta" to mapOf("total" to allCallModels.size)
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

        // Get a specific call
        get("/{call_id}") {
            val callId = call.parameters["call_id"] ?: throw BadRequestException("Missing call_id")
            val activeCalls = routes.callEngine.activeCalls.value
            val found = activeCalls.firstOrNull { it.id == callId }
            if (found == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to mapOf("code" to "NOT_FOUND", "message" to "Call $callId not found"))
                )
            } else {
                call.respond(mapOf("data" to found.toApiMap()))
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

private fun Call.toApiMap() = mapOf(
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
    "duration_seconds" to durationSeconds,
    "display_name" to displayName,
    "is_emergency" to isEmergency,
    "audio_state" to audioState.name
)
