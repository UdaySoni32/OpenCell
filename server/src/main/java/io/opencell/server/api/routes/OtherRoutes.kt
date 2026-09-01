package io.opencell.server.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import io.opencell.core.model.*
import io.opencell.server.api.ApiRoutes
import io.opencell.server.api.BadRequestException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

// ─── Contacts ───────────────────────────────────────────────────────────────

fun Route.contactsRoutes(routes: ApiRoutes) {
    route("/contacts") {
        get {
            val query = call.parameters["q"] ?: ""
            val contacts = if (query.isBlank()) {
                routes.contactEngine.getAllContacts(limit = 200)
            } else {
                routes.contactEngine.searchContacts(query, limit = 50)
            }
            call.respond(mapOf(
                "data" to contacts.map { c ->
                    mapOf(
                        "id" to c.id,
                        "display_name" to c.displayName,
                        "phone_numbers" to c.phoneNumbers.map { p ->
                            mapOf("number" to p.number, "type" to p.type)
                        }
                    )
                },
                "meta" to mapOf("total" to contacts.size)
            ))
        }

        get("/lookup") {
            val number = call.parameters["number"] ?: throw BadRequestException("'number' param required")
            val contact = routes.contactEngine.lookupByPhoneNumber(number)
            if (contact == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("data" to null))
            } else {
                call.respond(mapOf(
                    "data" to mapOf(
                        "id" to contact.id,
                        "display_name" to contact.displayName,
                        "phone_numbers" to contact.phoneNumbers.map { p ->
                            mapOf("number" to p.number, "type" to p.type)
                        }
                    )
                ))
            }
        }
    }
}

// ─── USSD ────────────────────────────────────────────────────────────────────

fun Route.ussdRoutes(routes: ApiRoutes) {
    route("/ussd") {
        post {
            // USSD is experimental — acknowledge and document the limitation
            call.respond(HttpStatusCode.NotImplemented, mapOf(
                "error" to mapOf(
                    "code" to ErrorCodes.CAPABILITY_UNSUPPORTED,
                    "message" to "USSD automation is not yet implemented. It requires the default dialer role and carrier cooperation."
                )
            ))
        }
    }
}

// ─── Events ─────────────────────────────────────────────────────────────────

fun Route.eventsRoutes(
    routes: ApiRoutes,
    wsClients: ConcurrentHashMap<String, WebSocketSession>
) {
    route("/events") {
        // GET /events — returns recent events from the database
        get {
            val deviceId = call.parameters["device_id"]
            val sinceSeq = call.parameters["since_seq"]?.toLongOrNull() ?: 0L
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 100

            val events = if (!deviceId.isNullOrBlank()) {
                routes.eventEngine.getEventsForDevice(deviceId, limit).first()
            } else if (sinceSeq > 0) {
                // Get events since a specific sequence number for any device
                // We need to get events from all devices since the given sequence
                routes.eventEngine.getRecentEvents(limit).first()
            } else {
                routes.eventEngine.getRecentEvents(limit).first()
            }

            call.respond(mapOf(
                "data" to events.map { evt ->
                    mapOf(
                        "id" to evt.id,
                        "name" to evt.name,
                        "device_id" to evt.deviceId,
                        "data" to evt.data,
                        "timestamp" to evt.timestamp,
                        "sequence_number" to evt.sequenceNumber,
                        "request_id" to evt.requestId
                    )
                },
                "meta" to mapOf(
                    "total" to events.size,
                    "limit" to limit,
                    "note" to "Use WebSocket /v1/events/stream for real-time events"
                )
            ))
        }
    }
}

// ─── Webhooks ────────────────────────────────────────────────────────────────

fun Route.webhooksRoutes(routes: ApiRoutes) {
    route("/webhooks") {
        get {
            call.respond(mapOf(
                "data" to emptyList<Any>(),
                "meta" to mapOf("total" to 0, "note" to "Webhooks coming in a future release")
            ))
        }
        post {
            call.respond(HttpStatusCode.NotImplemented, mapOf(
                "error" to mapOf("code" to "NOT_IMPLEMENTED", "message" to "Webhooks coming in a future release")
            ))
        }
        delete("/{webhook_id}") {
            call.respond(HttpStatusCode.NotImplemented, mapOf(
                "error" to mapOf("code" to "NOT_IMPLEMENTED", "message" to "Webhooks coming in a future release")
            ))
        }
    }
}

// ─── Projects ────────────────────────────────────────────────────────────────

fun Route.projectsRoutes(routes: ApiRoutes) {
    route("/projects") {
        get {
            call.respond(mapOf(
                "data" to listOf(mapOf("id" to "default", "name" to "Default Project")),
                "meta" to mapOf("total" to 1)
            ))
        }
        post {
            call.respond(HttpStatusCode.NotImplemented, mapOf(
                "error" to mapOf("code" to "NOT_IMPLEMENTED", "message" to "Multiple projects coming in a future release")
            ))
        }
    }
}

// ─── API Keys ────────────────────────────────────────────────────────────────

fun Route.apiKeyRoutes(routes: ApiRoutes) {
    route("/api-keys") {
        // List API keys — returns keys from DB (without hashes for security)
        get {
            val projectId = call.parameters["project_id"] ?: "default"
            val keys = routes.authService.listApiKeys(projectId)
            call.respond(mapOf(
                "data" to keys.map { key ->
                    mapOf(
                        "id" to key.id,
                        "name" to key.name,
                        "key_prefix" to key.keyPrefix,
                        "is_active" to key.isActive,
                        "created_at" to key.createdAt,
                        "expires_at" to key.expiresAt,
                        "last_used_at" to key.lastUsedAt,
                        "revoked_at" to key.revokedAt
                    )
                },
                "meta" to mapOf("total" to keys.size)
            ))
        }
        post {
            val body = call.receive<CreateApiKeyRequest>()
            if (body.name.isBlank()) throw BadRequestException("'name' is required")
            val (rawKey, entity) = routes.authService.createApiKey(
                projectId = "default",
                name = body.name,
                scopes = body.scopes.ifEmpty { listOf("*") },
                expiresInDays = body.expires_in_days
            )
            call.respond(HttpStatusCode.Created, mapOf(
                "data" to mapOf(
                    "id" to entity.id,
                    "name" to entity.name,
                    "key" to rawKey, // Raw key shown ONLY at creation
                    "key_prefix" to entity.keyPrefix,
                    "scopes" to body.scopes,
                    "created_at" to entity.createdAt,
                    "expires_at" to entity.expiresAt,
                    "warning" to "Save this key now. It will not be shown again."
                )
            ))
        }
        delete("/{key_id}") {
            val keyId = call.parameters["key_id"] ?: throw BadRequestException("Missing key_id")
            val revoked = routes.authService.revokeApiKey(keyId, revokedBy = "api")
            if (!revoked) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to mapOf("code" to "NOT_FOUND", "message" to "API key not found")))
            } else {
                call.respond(mapOf("data" to mapOf("id" to keyId, "revoked" to true)))
            }
        }
    }
}

// ─── Audit Logs ──────────────────────────────────────────────────────────────

fun Route.auditLogRoutes(routes: ApiRoutes) {
    route("/audit-logs") {
        get {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 100
            val logs = routes.authService.getRecentAuditLogs(limit)
            call.respond(mapOf(
                "data" to logs.map { log ->
                    mapOf(
                        "id" to log.id,
                        "action" to log.action,
                        "actor_type" to log.actorType,
                        "actor_id" to log.actorId,
                        "resource_type" to log.resourceType,
                        "resource_id" to log.resourceId,
                        "details" to log.details,
                        "timestamp" to log.timestamp
                    )
                },
                "meta" to mapOf("total" to logs.size)
            ))
        }
    }
}

// ─── Network ────────────────────────────────────────────────────────────────

fun Route.networkRoutes(routes: ApiRoutes) {
    route("/devices/{device_id}/network") {
        get {
            val networkInfo = routes.deviceEngine.getNetworkInfo()
            call.respond(mapOf(
                "data" to mapOf(
                    "type" to networkInfo.type,
                    "name" to networkInfo.name,
                    "is_available" to networkInfo.isAvailable,
                    "is_connected" to networkInfo.isConnected,
                    "is_roaming" to networkInfo.isRoaming,
                    "signal_strength" to networkInfo.signalStrength,
                    "signal_level" to networkInfo.signalLevel,
                    "asu_level" to networkInfo.asuLevel,
                    "dbm_level" to networkInfo.dbmLevel
                )
            ))
        }
    }
}

// ─── SIM ─────────────────────────────────────────────────────────────────────

fun Route.simRoutes(routes: ApiRoutes) {
    route("/devices/{device_id}/sim") {
        get {
            val simInfoList = routes.deviceEngine.getSimInfo()
            call.respond(mapOf(
                "data" to simInfoList.map { sim ->
                    mapOf(
                        "subscription_id" to sim.subscriptionId,
                        "slot_index" to sim.slotIndex,
                        "carrier_name" to sim.carrierName,
                        "display_name" to sim.displayName,
                        "number" to sim.number,
                        "mcc" to sim.mcc,
                        "mnc" to sim.mnc,
                        "country_code" to sim.countryCode,
                        "is_active" to sim.isActive,
                        "is_embedded" to sim.isEmbedded
                    )
                },
                "meta" to mapOf("total" to simInfoList.size)
            ))
        }
    }
}

// ─── Capabilities ────────────────────────────────────────────────────────────

fun Route.capabilitiesRoutes(routes: ApiRoutes) {
    route("/devices/{device_id}/capabilities") {
        get {
            val capabilities = routes.capabilityEngine.getAllCapabilities()
            call.respond(mapOf(
                "data" to capabilities.map { cap ->
                    mapOf(
                        "capability" to cap.capability,
                        "status" to cap.status.name,
                        "reason" to cap.reason.takeIf { it.isNotBlank() },
                        "device_model" to cap.deviceModel.takeIf { it.isNotBlank() },
                        "android_version" to cap.androidVersion.takeIf { it.isNotBlank() }
                    )
                },
                "meta" to mapOf("total" to capabilities.size)
            ))
        }
    }
}
