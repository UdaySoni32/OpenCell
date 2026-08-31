package io.opencell.server.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencell.core.model.ApiError
import io.opencell.core.model.ApiErrorResponse
import io.opencell.core.model.ErrorCodes
import io.opencell.server.api.ApiRoutes
import io.opencell.core.database.entity.DeviceEntity

fun Route.devicesRoutes(routes: ApiRoutes) {
    route("/devices") {
        get {
            val device = routes.deviceEngine.getOrCreateLocalDevice()
            call.respond(mapOf(
                "data" to listOf(mapOf(
                    "id" to device.id,
                    "name" to device.name,
                    "model" to device.model,
                    "manufacturer" to device.manufacturer,
                    "android_version" to device.androidVersion,
                    "sdk_version" to device.sdkVersion,
                    "is_active" to device.isActive,
                    "is_online" to device.isOnline,
                    "created_at" to device.createdAt,
                    "last_seen_at" to device.lastSeenAt
                )),
                "meta" to mapOf("total" to 1)
            ))
        }

        get("/{device_id}") {
            val deviceId = call.parameters["device_id"]
            val device = routes.deviceEngine.getOrCreateLocalDevice()

            if (deviceId != null && deviceId != "local" && deviceId != device.id) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiErrorResponse(ApiError(
                        code = ErrorCodes.DEVICE_OFFLINE,
                        message = "Device not found: $deviceId"
                    ))
                )
                return@get
            }

            call.respond(mapOf(
                "data" to mapOf(
                    "id" to device.id,
                    "name" to device.name,
                    "model" to device.model,
                    "manufacturer" to device.manufacturer,
                    "android_version" to device.androidVersion,
                    "sdk_version" to device.sdkVersion,
                    "is_active" to device.isActive,
                    "is_online" to device.isOnline,
                    "created_at" to device.createdAt,
                    "last_seen_at" to device.lastSeenAt
                )
            ))
        }
    }
}
