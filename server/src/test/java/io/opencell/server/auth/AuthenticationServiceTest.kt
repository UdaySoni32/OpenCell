package io.opencell.server.auth

import io.opencell.core.database.dao.ApiKeyDao
import io.opencell.core.database.dao.AuditLogDao
import io.opencell.core.database.entity.ApiKeyEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthenticationServiceTest {

    private lateinit var apiKeyDao: ApiKeyDao
    private lateinit var auditLogDao: AuditLogDao
    private lateinit var authService: AuthenticationService

    @Before
    fun setup() {
        apiKeyDao = mockk(relaxed = true)
        auditLogDao = mockk(relaxed = true)
        authService = AuthenticationService(apiKeyDao, auditLogDao)
    }

    @Test
    fun `createApiKey generates a valid key and stores it`() = runTest {
        val projectId = "proj_123"
        val name = "Test Key"
        val scopes = listOf("read:messages")

        val (rawKey, entity) = authService.createApiKey(projectId, name, scopes)

        assertTrue(rawKey.startsWith("oc_test_"))
        assertEquals(projectId, entity.projectId)
        assertEquals(name, entity.name)
        
        coVerify { apiKeyDao.upsertApiKey(any()) }
        coVerify { auditLogDao.insertEntry(any()) }
    }

    @Test
    fun `authenticate returns success for valid key`() = runTest {
        val rawKey = "oc_test_valid_key"
        val projectId = "proj_123"
        val scopes = "[\"*\"]"
        
        val entity = ApiKeyEntity(
            id = "key_1",
            projectId = projectId,
            name = "Test",
            keyPrefix = "oc_test...",
            keyHash = io.opencell.core.crypto.CryptoUtils.hashApiKey(rawKey),
            scopes = scopes,
            isActive = true
        )

        coEvery { apiKeyDao.getApiKeyByHash(any()) } returns entity

        val result = authService.authenticate(rawKey)

        assertTrue(result.authenticated)
        assertEquals(projectId, result.projectId)
        coVerify { apiKeyDao.updateLastUsed(entity.id) }
    }

    @Test
    fun `authenticate returns failure for revoked key`() = runTest {
        val rawKey = "oc_test_revoked"
        val entity = ApiKeyEntity(
            id = "key_1",
            projectId = "p1",
            name = "Test",
            keyPrefix = "...",
            keyHash = io.opencell.core.crypto.CryptoUtils.hashApiKey(rawKey),
            scopes = "[]",
            isActive = false
        )

        coEvery { apiKeyDao.getApiKeyByHash(any()) } returns entity

        val result = authService.authenticate(rawKey)

        assertFalse(result.authenticated)
        assertEquals("API key has been revoked", result.error)
    }

    @Test
    fun `hasScope correctly validates permissions`() {
        val scopes = listOf("messages:read", "calls:*")
        
        assertTrue(authService.hasScope(scopes, "messages:read"))
        assertTrue(authService.hasScope(scopes, "calls:list"))
        assertTrue(authService.hasScope(scopes, "calls:create"))
        assertFalse(authService.hasScope(scopes, "messages:write"))
        
        assertTrue(authService.hasScope(listOf("*"), "anything"))
    }
}
