package io.opencell.server.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencell.core.model.*
import io.opencell.server.api.ApiRoutes
import io.opencell.server.api.BadRequestException
import kotlinx.coroutines.flow.first

fun Route.messagesRoutes(routes: ApiRoutes) {
    route("/messages") {

        // List recent messages
        get {
            val messages = routes.messagingEngine.getAllMessages().first()
            call.respond(mapOf(
                "data" to messages.map { it.toApiMap() },
                "meta" to mapOf("total" to messages.size)
            ))
        }

        // Send an SMS
        post {
            val request = call.receive<SendMessageRequest>()
            if (request.to.isBlank()) throw BadRequestException("'to' is required")
            if (request.body.isBlank()) throw BadRequestException("'body' is required")

            val deviceId = routes.deviceEngine.getLocalDeviceId()
            val result = routes.messagingEngine.sendSms(
                to = request.to,
                body = request.body,
                deviceId = deviceId,
                subscriptionId = request.subscription_id
            )

            result.fold(
                onSuccess = { message ->
                    call.respond(HttpStatusCode.Created, mapOf("data" to message.toApiMap()))
                },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf("error" to mapOf(
                            "code" to ErrorCodes.TELEPHONY_ERROR,
                            "message" to (e.message ?: "Failed to send SMS")
                        ))
                    )
                }
            )
        }

        // Get a specific message
        get("/{message_id}") {
            val messageId = call.parameters["message_id"] ?: throw BadRequestException("Missing message_id")
            val message = routes.messagingEngine.getMessage(messageId)
            if (message == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to mapOf("code" to "NOT_FOUND", "message" to "Message not found"))
                )
            } else {
                call.respond(mapOf("data" to message.toApiMap()))
            }
        }
    }

    route("/conversations") {
        get {
            val conversations = routes.messagingEngine.getConversations().first()
            call.respond(mapOf(
                "data" to conversations.map { it.toApiMap() },
                "meta" to mapOf("total" to conversations.size)
            ))
        }

        get("/{thread_id}/messages") {
            val threadId = call.parameters["thread_id"] ?: throw BadRequestException("Missing thread_id")
            val messages = routes.messagingEngine.getMessagesByThread(threadId).first()
            call.respond(mapOf(
                "data" to messages.map { it.toApiMap() },
                "meta" to mapOf("total" to messages.size)
            ))
        }
    }
}

private fun Message.toApiMap() = mapOf(
    "id" to id,
    "device_id" to deviceId,
    "subscription_id" to subscriptionId,
    "type" to type.name,
    "direction" to direction.name,
    "from" to sender,
    "to" to recipient,
    "body" to body,
    "state" to state.name,
    "created_at" to createdAt,
    "sent_at" to sentAt,
    "delivered_at" to deliveredAt,
    "thread_id" to threadId,
    "error_message" to errorMessage
)

private fun Conversation.toApiMap() = mapOf(
    "thread_id" to threadId,
    "contact_address" to contactAddress,
    "contact_name" to contactName,
    "last_message" to lastMessage,
    "last_message_at" to lastMessageAt,
    "unread_count" to unreadCount,
    "is_group" to isGroup
)
