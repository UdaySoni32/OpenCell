package io.opencell.server.auth

import io.opencell.core.crypto.CryptoUtils
import io.opencell.core.database.dao.ApiKeyDao
import io.opencell.core.database.dao.AuditLogDao
import io.opencell.core.database.entity.ApiKeyEntity
import io.opencell.core.database.entity.AuditLogEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationService @Inject constructor(
    private val apiKeyDao: ApiKeyDao,
    private val auditLogDao: AuditLogDao
) {
    private val keyCache = ConcurrentHashMap<String, CachedApiKey>()
    private val rateLimitTracker = ConcurrentHashMap<String, MutableList<Long>>()

    data class CachedApiKey(val id: String, val projectId: String, val scopes: List<String>, val isActive: Boolean, val maxRatePerMinute: Int = 60)
    data class AuthResult(val authenticated: Boolean, val apiKeyId: String? = null, val projectId: String? = null, val scopes: List<String> = emptyList(), val error: String? = null)

    suspend fun createApiKey(projectId: String, name: String, scopes: List<String>, expiresInDays: Int? = null): Pair<String, ApiKeyEntity> {
        val keyId = CryptoUtils.generateId("key")
        val rawKey = CryptoUtils.generateApiKey("oc_test")
        val keyHash = CryptoUtils.hashApiKey(rawKey)
        val keyPrefix = rawKey.take(14) + "..."
        val expiresAt = expiresInDays?.let { System.currentTimeMillis() + (it.toLong() * 24 * 60 * 60 * 1000) }
        val scopesJson = Json.encodeToString(ListSerializer(String.serializer()), scopes)

        val entity = ApiKeyEntity(
            id = keyId, projectId = projectId, name = name, keyPrefix = keyPrefix,
            keyHash = keyHash, scopes = scopesJson, isActive = true,
            createdAt = System.currentTimeMillis(), expiresAt = expiresAt
        )
        apiKeyDao.upsertApiKey(entity)
        keyCache[keyHash] = CachedApiKey(id = keyId, projectId = projectId, scopes = scopes, isActive = true)

        auditLogDao.insertEntry(AuditLogEntity(
            id = CryptoUtils.generateId("audit"), action = "api_key.created",
            actorType = "system", actorId = projectId, resourceType = "api_key",
            resourceId = keyId, details = """{"name":"$name","scopes":"${scopes.joinToString(",")}"}"""
        ))
        return Pair(rawKey, entity)
    }

    suspend fun authenticate(apiKey: String): AuthResult {
        val keyHash = CryptoUtils.hashApiKey(apiKey)
        val cached = keyCache[keyHash]
        if (cached != null) {
            if (!cached.isActive) return AuthResult(authenticated = false, error = "API key has been revoked")
            val entity = apiKeyDao.getApiKeyByHash(keyHash)
            val exp = entity?.expiresAt
            if (exp != null && exp < System.currentTimeMillis()) {
                return AuthResult(authenticated = false, error = "API key has expired")
            }
            if (!checkRateLimit(keyHash, cached.maxRatePerMinute)) {
                return AuthResult(authenticated = false, error = "Rate limit exceeded")
            }
            apiKeyDao.updateLastUsed(cached.id)
            return AuthResult(authenticated = true, apiKeyId = cached.id, projectId = cached.projectId, scopes = cached.scopes)
        }

        val entity = apiKeyDao.getApiKeyByHash(keyHash) ?: return AuthResult(authenticated = false, error = "Invalid API key")
        if (!entity.isActive) return AuthResult(authenticated = false, error = "API key has been revoked")
        val exp = entity.expiresAt
        if (exp != null && exp < System.currentTimeMillis()) {
            return AuthResult(authenticated = false, error = "API key has expired")
        }
        val scopes = parseScopes(entity.scopes)
        keyCache[keyHash] = CachedApiKey(id = entity.id, projectId = entity.projectId, scopes = scopes, isActive = true)
        apiKeyDao.updateLastUsed(entity.id)
        return AuthResult(authenticated = true, apiKeyId = entity.id, projectId = entity.projectId, scopes = scopes)
    }

    fun hasScope(scopes: List<String>, requiredScope: String): Boolean {
        if (scopes.contains("*")) return true
        return scopes.any { scope -> scope == requiredScope || (scope.endsWith(":*") && requiredScope.startsWith(scope.dropLast(1))) }
    }

    suspend fun revokeApiKey(keyId: String, revokedBy: String = "system"): Boolean {
        val entity = apiKeyDao.getApiKey(keyId) ?: return false
        apiKeyDao.revokeApiKey(keyId)
        keyCache.remove(entity.keyHash)
        auditLogDao.insertEntry(AuditLogEntity(
            id = CryptoUtils.generateId("audit"), action = "api_key.revoked",
            actorType = "system", actorId = revokedBy, resourceType = "api_key",
            resourceId = keyId, details = """{"key_name":"${entity.name}"}"""
        ))
        return true
    }

    fun extractApiKey(authorizationHeader: String?): String? {
        if (authorizationHeader == null) return null
        val parts = authorizationHeader.trim().split(" ", limit = 2)
        if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
        return parts[1]
    }

    private fun checkRateLimit(keyHash: String, maxPerMinute: Int): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000
        val timestamps = rateLimitTracker.getOrPut(keyHash) { mutableListOf() }
        timestamps.removeAll { it < oneMinuteAgo }
        if (timestamps.size >= maxPerMinute) return false
        timestamps.add(now)
        return true
    }

    private fun parseScopes(scopesJson: String): List<String> {
        return try { Json.decodeFromString<List<String>>(scopesJson) } catch (e: Exception) { emptyList() }
    }
}
