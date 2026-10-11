package com.opencell.gateway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePairedDeviceDao : PairedDeviceDao {
    val rows = MutableStateFlow<List<PairedDeviceEntity>>(emptyList())
    override suspend fun insert(device: PairedDeviceEntity): Long {
        val id = rows.value.size + 1L
        rows.value = rows.value + device.copy(id = id)
        return id
    }
    override suspend fun update(device: PairedDeviceEntity) {
        rows.value = rows.value.map { if (it.id == device.id) device else it }
    }
    override suspend fun findActive(hash: String) = rows.value.firstOrNull { it.tokenHash == hash && !it.revoked }
    override suspend fun find(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun touch(id: Long, at: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(lastSeenMillis = at) else it }
    }
    override fun observeActive(): Flow<List<PairedDeviceEntity>> = rows.map { list -> list.filter { !it.revoked } }
}

class PairingManagerTest {
    private val dao = FakePairedDeviceDao()
    private var clock = 1_000L
    private val pairing = PairingManager(dao) { clock }

    @Test
    fun `correct code pairs once and token authenticates`() = runTest {
        val code = pairing.startPairing().code
        val res = pairing.pair(code, "laptop")!!
        assertTrue(res.token.startsWith("oc_"))
        assertNull("code is single use", pairing.pair(code, "again"))
        val device = pairing.authenticate(res.token)!!
        assertEquals("laptop", device.name)
        assertTrue("only the hash is stored", dao.rows.value.none { it.tokenHash == res.token })
    }

    @Test
    fun `expired code is rejected`() = runTest {
        val code = pairing.startPairing().code
        clock += PairingManager.CODE_TTL_MS + 1
        assertNull(pairing.pair(code, "laptop"))
    }

    @Test
    fun `too many wrong guesses burn the code`() = runTest {
        val code = pairing.startPairing().code
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(PairingManager.MAX_ATTEMPTS) { assertNull(pairing.pair(wrong, "x")) }
        assertNull(pairing.pair(code, "x"))
    }

    @Test
    fun `revoked device no longer authenticates`() = runTest {
        val res = pairing.pair(pairing.startPairing().code, "pi")!!
        assertNotNull(pairing.authenticate(res.token))
        dao.update(dao.rows.value.single().copy(revoked = true))
        assertNull(pairing.authenticate(res.token))
        assertNull(pairing.authenticate("oc_bogus"))
    }
}
