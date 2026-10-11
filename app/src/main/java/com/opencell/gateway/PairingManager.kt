package com.opencell.gateway

import com.opencell.policy.Actor
import com.opencell.policy.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PairingCode(val code: String, val expiresAtMillis: Long)

/**
 * One-time pairing codes and device tokens. A code is shown on the phone only, lives 5 minutes,
 * and is burned after one success or [MAX_ATTEMPTS] wrong guesses.
 */
class PairingManager(private val dao: PairedDeviceDao, private val now: () -> Long = System::currentTimeMillis) {

    private val random = SecureRandom()
    private val _code = MutableStateFlow<PairingCode?>(null)
    val code: StateFlow<PairingCode?> = _code.asStateFlow()
    private var failedAttempts = 0

    @Synchronized
    fun startPairing(): PairingCode {
        val code = PairingCode((0 until CODE_LENGTH).joinToString("") { random.nextInt(10).toString() }, now() + CODE_TTL_MS)
        failedAttempts = 0
        _code.value = code
        return code
    }

    fun cancelPairing() {
        _code.value = null
    }

    suspend fun pair(code: String, name: String): PairResponse? {
        if (!consume(code)) return null
        val token = newToken()
        val scopes = Scope.DEFAULT_DEVICE
        val id = dao.insert(
            PairedDeviceEntity(name = name, tokenHash = sha256(token), scopes = Scope.format(scopes), createdAtMillis = now()),
        )
        return PairResponse(token = token, deviceId = id, scopes = scopes.map { it.wire })
    }

    suspend fun authenticate(token: String): Actor.Device? {
        if (token.isBlank()) return null
        val device = dao.findActive(sha256(token)) ?: return null
        dao.touch(device.id, now())
        return device.toActor()
    }

    @Synchronized
    private fun consume(attempt: String): Boolean {
        val current = _code.value ?: return false
        if (now() > current.expiresAtMillis) {
            _code.value = null
            return false
        }
        // Constant-time compare so the code can't be guessed digit by digit from timing.
        if (MessageDigest.isEqual(current.code.toByteArray(), attempt.toByteArray())) {
            _code.value = null
            return true
        }
        if (++failedAttempts >= MAX_ATTEMPTS) _code.value = null
        return false
    }

    private fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return "oc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val CODE_LENGTH = 6
        const val CODE_TTL_MS = 5 * 60_000L
        const val MAX_ATTEMPTS = 5

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
