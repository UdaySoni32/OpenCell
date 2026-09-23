package com.example.opencell.gateway

import com.example.opencell.domain.model.ApiKeyRecord
import com.example.opencell.gateway.server.CallRequestDto
import com.example.opencell.gateway.server.CapabilitiesResponseDto
import com.example.opencell.gateway.server.DeviceResponseDto
import com.example.opencell.gateway.server.HealthResponseDto
import com.example.opencell.gateway.server.MessageRequestDto
import com.example.opencell.gateway.server.SimInfoResponseDto
import com.example.opencell.gateway.server.SimulateIncomingCallRequestDto
import com.example.opencell.gateway.server.WebhookRequestDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayServerApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testHealthResponseSerialization() {
        val dto = HealthResponseDto(
            status = "OK",
            uptimeMs = 12000L,
            activeWebsockets = 2,
            version = "1.0.0"
        )

        val jsonStr = json.encodeToString(dto)
        assertTrue(jsonStr.contains("\"status\":\"OK\""))
        assertTrue(jsonStr.contains("\"uptimeMs\":12000"))
        assertTrue(jsonStr.contains("\"activeWebsockets\":2"))

        val decoded = json.decodeFromString<HealthResponseDto>(jsonStr)
        assertEquals("OK", decoded.status)
        assertEquals(12000L, decoded.uptimeMs)
        assertEquals(2, decoded.activeWebsockets)
    }

    @Test
    fun testDeviceResponseSerialization() {
        val dto = DeviceResponseDto(
            model = "Pixel 8 Pro",
            manufacturer = "Google",
            sdkVersion = 35,
            batteryLevelPercent = 95,
            isCharging = true
        )

        val jsonStr = json.encodeToString(dto)
        val decoded = json.decodeFromString<DeviceResponseDto>(jsonStr)
        assertEquals("Pixel 8 Pro", decoded.model)
        assertEquals("Google", decoded.manufacturer)
        assertEquals(35, decoded.sdkVersion)
        assertEquals(95, decoded.batteryLevelPercent)
        assertTrue(decoded.isCharging)
    }

    @Test
    fun testCapabilitiesResponseSerialization() {
        val dto = CapabilitiesResponseDto(
            telephonyCapable = true,
            smsCapable = true,
            callPermissionGranted = true,
            activeSimCount = 1
        )

        val jsonStr = json.encodeToString(dto)
        val decoded = json.decodeFromString<CapabilitiesResponseDto>(jsonStr)
        assertTrue(decoded.telephonyCapable)
        assertTrue(decoded.smsCapable)
        assertEquals(1, decoded.activeSimCount)
    }

    @Test
    fun testSimInfoResponseSerialization() {
        val dto = SimInfoResponseDto(
            simState = "READY",
            carrierName = "T-Mobile",
            mccMnc = "310260",
            countryIso = "us",
            activeSimCount = 1
        )

        val jsonStr = json.encodeToString(dto)
        val decoded = json.decodeFromString<SimInfoResponseDto>(jsonStr)
        assertEquals("READY", decoded.simState)
        assertEquals("T-Mobile", decoded.carrierName)
        assertEquals("310260", decoded.mccMnc)
    }

    @Test
    fun testCallRequestDtoDeserializationWithToAlias() {
        val legacyJson = "{\"to\":\"+15551234567\",\"contactName\":\"Demo Contact\"}"
        val dto = json.decodeFromString<CallRequestDto>(legacyJson)
        assertEquals("+15551234567", dto.phoneNumber)
        assertEquals("Demo Contact", dto.contactName)

        val standardJson = "{\"phoneNumber\":\"+15559876543\",\"contactName\":\"Alice\"}"
        val standardDto = json.decodeFromString<CallRequestDto>(standardJson)
        assertEquals("+15559876543", standardDto.phoneNumber)
        assertEquals("Alice", standardDto.contactName)
    }

    @Test
    fun testSimulateIncomingCallDtoDeserializationWithAliases() {
        val aliasJson = "{\"to\":\"+15550001111\",\"callerName\":\"Bob\"}"
        val dto = json.decodeFromString<SimulateIncomingCallRequestDto>(aliasJson)
        assertEquals("+15550001111", dto.phoneNumber)
        assertEquals("Bob", dto.callerName)
    }

    @Test
    fun testMessageRequestDtoSerialization() {
        val dto = MessageRequestDto(
            recipient = "+15554443333",
            body = "Test message body from API",
            contactName = "Test Recipient"
        )

        val jsonStr = json.encodeToString(dto)
        val decoded = json.decodeFromString<MessageRequestDto>(jsonStr)
        assertEquals("+15554443333", decoded.recipient)
        assertEquals("Test message body from API", decoded.body)
        assertEquals("Test Recipient", decoded.contactName)
    }

    @Test
    fun testWebhookRequestDtoSerialization() {
        val dto = WebhookRequestDto(
            url = "https://example.com/api/webhook",
            events = listOf("call.created", "message.created"),
            secret = "whsec_test123"
        )

        val jsonStr = json.encodeToString(dto)
        val decoded = json.decodeFromString<WebhookRequestDto>(jsonStr)
        assertEquals("https://example.com/api/webhook", decoded.url)
        assertEquals(2, decoded.events.size)
        assertEquals("whsec_test123", decoded.secret)
    }

    @Test
    fun testApiKeyScopesValidation() {
        val masterKey = ApiKeyRecord(
            id = "master_1",
            name = "Master Key",
            key = "oc_live_master_key_123456",
            scopes = ApiKeyRecord.ALL_SCOPES
        )

        assertTrue(masterKey.scopes.contains("*") || masterKey.scopes.containsAll(
            listOf(
                ApiKeyRecord.SCOPE_CALLS_READ,
                ApiKeyRecord.SCOPE_CALLS_CREATE,
                ApiKeyRecord.SCOPE_CALLS_CONTROL,
                ApiKeyRecord.SCOPE_MESSAGES_READ,
                ApiKeyRecord.SCOPE_MESSAGES_CREATE,
                ApiKeyRecord.SCOPE_CONTACTS_READ,
                ApiKeyRecord.SCOPE_SIM_READ,
                ApiKeyRecord.SCOPE_NETWORK_READ,
                ApiKeyRecord.SCOPE_EVENTS_READ
            )
        ))
    }
}
