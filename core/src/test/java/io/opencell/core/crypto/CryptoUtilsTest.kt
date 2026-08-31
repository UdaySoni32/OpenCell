package io.opencell.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun `generateSecureRandom returns string of correct length`() {
        val length = 32
        val randomString = CryptoUtils.generateSecureRandom(length)
        // Hex string length is 2 * byte length
        assertEquals(length * 2, randomString.length)
    }

    @Test
    fun `generateApiKey returns string with correct prefix`() {
        val prefix = "oc_test"
        val apiKey = CryptoUtils.generateApiKey(prefix)
        assertTrue(apiKey.startsWith(prefix))
        assertTrue(apiKey.contains("_"))
    }

    @Test
    fun `hashApiKey produces consistent hash`() {
        val key = "oc_test_12345"
        val hash1 = CryptoUtils.hashApiKey(key)
        val hash2 = CryptoUtils.hashApiKey(key)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashApiKey produces different hashes for different keys`() {
        val key1 = "oc_test_1"
        val key2 = "oc_test_2"
        assertNotEquals(CryptoUtils.hashApiKey(key1), CryptoUtils.hashApiKey(key2))
    }

    @Test
    fun `computeHmacSha256 produces consistent signature`() {
        val secret = "my_secret"
        val payload = "{\"event\":\"test\"}"
        val sig1 = CryptoUtils.computeHmacSha256(secret, payload)
        val sig2 = CryptoUtils.computeHmacSha256(secret, payload)
        assertEquals(sig1, sig2)
    }

    @Test
    fun `verifyHmacSha256 returns true for valid signature`() {
        val secret = "my_secret"
        val payload = "{\"event\":\"test\"}"
        val signature = CryptoUtils.computeHmacSha256(secret, payload)
        assertTrue(CryptoUtils.verifyHmacSha256(secret, payload, signature))
    }

    @Test
    fun `generateId returns string with prefix`() {
        val prefix = "usr"
        val id = CryptoUtils.generateId(prefix)
        assertTrue(id.startsWith("${prefix}_"))
    }
}
