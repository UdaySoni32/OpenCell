package com.example.opencell.gateway

import com.example.opencell.domain.model.ApiKeyRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyAuthenticationTest {

    @Test
    fun testApiKeyPrefixValidation() {
        val liveKey = ApiKeyRecord(
            id = "1",
            name = "Live Key",
            key = "oc_live_abc123xyz789",
            scopes = ApiKeyRecord.ALL_SCOPES
        )

        val testKey = ApiKeyRecord(
            id = "2",
            name = "Test Key",
            key = "oc_test_qwerty123456",
            scopes = ApiKeyRecord.ALL_SCOPES
        )

        val invalidKey = ApiKeyRecord(
            id = "3",
            name = "Invalid Key",
            key = "invalid_prefix_key_123",
            scopes = ApiKeyRecord.ALL_SCOPES
        )

        assertTrue(liveKey.key.startsWith("oc_live_"))
        assertTrue(testKey.key.startsWith("oc_test_"))
        assertFalse(invalidKey.key.startsWith("oc_live_") || invalidKey.key.startsWith("oc_test_"))
    }

    @Test
    fun testApiKeyScopeEnforcement() {
        val callsOnlyKey = ApiKeyRecord(
            id = "4",
            name = "Calls Only",
            key = "oc_live_calls_123",
            scopes = listOf(ApiKeyRecord.SCOPE_CALLS_READ, ApiKeyRecord.SCOPE_CALLS_CREATE)
        )

        assertTrue(callsOnlyKey.scopes.contains(ApiKeyRecord.SCOPE_CALLS_READ))
        assertTrue(callsOnlyKey.scopes.contains(ApiKeyRecord.SCOPE_CALLS_CREATE))
        assertFalse(callsOnlyKey.scopes.contains(ApiKeyRecord.SCOPE_MESSAGES_CREATE))
        assertFalse(callsOnlyKey.scopes.contains(ApiKeyRecord.SCOPE_CONTACTS_READ))
    }
}
