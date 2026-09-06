package com.example.opencell.gateway.webhook

import com.example.opencell.data.local.preferences.DeveloperPreferencesRepository
import com.example.opencell.domain.model.ServerLogType
import com.example.opencell.domain.model.TelephonyEvent
import com.example.opencell.gateway.event.EventEngine
import com.example.opencell.gateway.logging.ServerLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookEngine(
    private val developerPreferencesRepository: DeveloperPreferencesRepository,
    private val eventEngine: EventEngine = EventEngine.instance,
    private val serverLogRepository: ServerLogRepository = ServerLogRepository.instance,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val json = Json { encodeDefaults = true }
    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch {
            eventEngine.events.collect { event ->
                dispatchToWebhooks(event)
            }
        }
        serverLogRepository.log(ServerLogType.SYSTEM, "WebhookEngine started listening for events")
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun dispatchToWebhooks(event: TelephonyEvent) {
        val registeredWebhooks = try {
            developerPreferencesRepository.webhooks.first()
        } catch (_: Exception) {
            emptyList()
        }

        if (registeredWebhooks.isEmpty()) return

        val jsonPayload = json.encodeToString(event)

        for (webhook in registeredWebhooks) {
            if (webhook.events.contains(event.eventType) || webhook.events.contains("*")) {
                deliverWebhook(webhook.url, webhook.secret, event, jsonPayload)
            }
        }
    }

    private fun deliverWebhook(
        url: String,
        secret: String,
        event: TelephonyEvent,
        jsonPayload: String
    ) {
        scope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val signature = computeHmacSha256(secret, "$timestamp.$jsonPayload")
                val headerValue = "t=$timestamp,v1=$signature"

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonPayload.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-OpenCell-Signature", headerValue)
                    .addHeader("X-OpenCell-Event", event.eventType)
                    .build()

                serverLogRepository.log(
                    type = ServerLogType.WEBHOOK,
                    message = "Dispatching Webhook: ${event.eventType} -> $url",
                    details = "Signature: $headerValue"
                )

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        serverLogRepository.log(
                            type = ServerLogType.WEBHOOK,
                            message = "Webhook Delivery Success: ${response.code} ($url)",
                            details = "Event: ${event.eventType}"
                        )
                    } else {
                        serverLogRepository.log(
                            type = ServerLogType.WEBHOOK,
                            message = "Webhook Delivery Failed: HTTP ${response.code} ($url)",
                            details = "Response Body: ${response.body?.string()}"
                        )
                    }
                }
            } catch (e: Exception) {
                serverLogRepository.log(
                    type = ServerLogType.WEBHOOK,
                    message = "Webhook Delivery Exception: ${e.message} ($url)",
                    details = e.stackTraceToString()
                )
            }
        }
    }

    companion object {
        fun computeHmacSha256(secret: String, payload: String): String {
            return try {
                val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(secretKeySpec)
                val bytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                ""
            }
        }
    }
}
