package io.opencell.core.model

import kotlinx.serialization.Serializable

/**
 * Standard API error response.
 */
@Serializable
data class ApiErrorResponse(
    val error: ApiError
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val request_id: String? = null,
    val details: Map<String, String>? = null
)

/**
 * Standard API success response wrapper.
 */
@Serializable
data class ApiResponse<T>(
    val data: T,
    val meta: ResponseMeta? = null
)

@Serializable
data class ResponseMeta(
    val total: Int? = null,
    val page: Int? = null,
    val per_page: Int? = null,
    val has_more: Boolean? = null,
    val request_id: String? = null
)

/**
 * Request models
 */
@Serializable
data class CreateApiKeyRequest(
    val name: String,
    val scopes: List<String> = emptyList(),
    val expires_in_days: Int? = null
)

@Serializable
data class SendMessageRequest(
    val to: String,
    val body: String,
    val from: String? = null,
    val subscription_id: Int? = null
)

@Serializable
data class MakeCallRequest(
    val to: String,
    val from: String? = null,
    val subscription_id: Int? = null
)

@Serializable
data class CreateWebhookRequest(
    val url: String,
    val events: List<String>,
    val secret: String? = null
)

@Serializable
data class UssdRequest(
    val code: String,
    val subscription_id: Int? = null
)

/**
 * List response for collections
 */
@Serializable
data class ListResponse<T>(
    val data: List<T>,
    val meta: ResponseMeta
)

/**
 * Error codes
 */
object ErrorCodes {
    const val AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR"
    const val AUTHORIZATION_ERROR = "AUTHORIZATION_ERROR"
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val DEVICE_OFFLINE = "DEVICE_OFFLINE"
    const val DEVICE_BUSY = "DEVICE_BUSY"
    const val CAPABILITY_UNSUPPORTED = "CAPABILITY_UNSUPPORTED"
    const val PERMISSION_REQUIRED = "PERMISSION_REQUIRED"
    const val TELEPHONY_ERROR = "TELEPHONY_ERROR"
    const val SIM_ERROR = "SIM_ERROR"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val COMMAND_TIMEOUT = "COMMAND_TIMEOUT"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val WEBHOOK_ERROR = "WEBHOOK_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
