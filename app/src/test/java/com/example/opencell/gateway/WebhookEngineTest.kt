package com.example.opencell.gateway

import com.example.opencell.gateway.webhook.WebhookEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookEngineTest {

    @Test
    fun testHmacSha256SignatureComputation() {
        val secret = "whsec_test_secret_key_12345"
        val timestamp = 1700000000000L
        val payload = "{\"eventType\":\"call.created\",\"payload\":{\"callId\":\"101\"}}"
        val dataToSign = "$timestamp.$payload"

        val signature1 = WebhookEngine.computeHmacSha256(secret, dataToSign)
        val signature2 = WebhookEngine.computeHmacSha256(secret, dataToSign)

        assertTrue(signature1.isNotBlank())
        assertEquals(64, signature1.length) // Hex string representation of SHA-256 (32 bytes = 64 hex chars)
        assertEquals(signature1, signature2)

        // Different payload should produce different signature
        val differentSignature = WebhookEngine.computeHmacSha256(secret, "$timestamp.otherPayload")
        assertNotEquals(signature1, differentSignature)
    }
}
