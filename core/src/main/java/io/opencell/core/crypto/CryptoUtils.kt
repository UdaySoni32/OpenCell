package io.opencell.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val secureRandom = SecureRandom()

    /**
     * Generate a cryptographically secure random string.
     */
    fun generateSecureRandom(length: Int = 32): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate an API key in the format: oc_test_xxxxxxxx or oc_live_xxxxxxxx
     */
    fun generateApiKey(prefix: String = "oc_test"): String {
        val randomPart = generateSecureRandom(24)
        return "${prefix}_${randomPart}"
    }

    /**
     * Hash an API key for secure storage using SHA-256.
     */
    fun hashApiKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(key.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute HMAC-SHA256 for webhook signatures.
     */
    fun computeHmacSha256(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val signature = mac.doFinal(payload.toByteArray())
        return signature.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify an HMAC-SHA256 signature.
     */
    fun verifyHmacSha256(secret: String, payload: String, expectedSignature: String): Boolean {
        val computedSignature = computeHmacSha256(secret, payload)
        return constantTimeEquals(computedSignature, expectedSignature)
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    /**
     * Generate a unique request ID.
     */
    fun generateRequestId(): String = "req_${generateSecureRandom(16)}"

    /**
     * Generate a unique entity ID.
     */
    fun generateId(prefix: String = ""): String {
        val id = generateSecureRandom(16)
        return if (prefix.isEmpty()) id else "${prefix}_$id"
    }
}
