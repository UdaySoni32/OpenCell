package com.opencell.calllog

import com.opencell.calllog.notification.CallEvent
import com.opencell.calllog.notification.CallEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeAppCallDao : AppCallDao {
    val rows = MutableStateFlow<List<AppCallEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<AppCallEntity>> = rows
    override suspend fun findRecent(packageName: String, peerName: String?, sinceMillis: Long) =
        rows.value.filter { it.packageName == packageName && it.peerName == peerName && it.startedAtMillis >= sinceMillis }
            .maxByOrNull { it.startedAtMillis }
    override suspend fun findById(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun insert(entity: AppCallEntity): Long {
        val id = nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: AppCallEntity) {
        rows.value = rows.value.map { if (it.id == entity.id) entity else it }
    }
}

class AppCallRecorderTest {
    private val dao = FakeAppCallDao()
    private val recorder = AppCallRecorder(dao)

    private fun ev(type: CallEventType, t: Long, peer: String? = "Riya") =
        CallEvent(CallSource.WHATSAPP, "com.whatsapp", type, peer, t, "key")

    @Test
    fun `ringing then missed becomes one missed row`() = runTest {
        recorder.onEvent(ev(CallEventType.RINGING, 1_000))
        recorder.onEvent(ev(CallEventType.MISSED, 20_000))
        assertEquals(listOf(CallKind.MISSED), dao.rows.value.map { it.kind })
    }

    @Test
    fun `ringing then ongoing then end records answered call with duration`() = runTest {
        recorder.onEvent(ev(CallEventType.RINGING, 1_000))
        recorder.onEvent(ev(CallEventType.ONGOING, 5_000))
        recorder.onCallEnded("com.whatsapp", "Riya", 65_000)
        val row = dao.rows.value.single()
        assertEquals(CallKind.INCOMING, row.kind)
        assertEquals(60, row.durationSeconds)
    }

    @Test
    fun `ongoing without ring is outgoing`() = runTest {
        recorder.onEvent(ev(CallEventType.ONGOING, 1_000))
        assertEquals(CallKind.OUTGOING, dao.rows.value.single().kind)
    }

    @Test
    fun `repeated notifications do not duplicate`() = runTest {
        recorder.onEvent(ev(CallEventType.RINGING, 1_000))
        recorder.onEvent(ev(CallEventType.RINGING, 2_000))
        recorder.onEvent(ev(CallEventType.MISSED, 3_000))
        recorder.onEvent(ev(CallEventType.MISSED, 4_000))
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `calls far apart are separate rows`() = runTest {
        recorder.onEvent(ev(CallEventType.MISSED, 0))
        recorder.onEvent(ev(CallEventType.MISSED, AppCallRecorder.SAME_CALL_WINDOW_MS + 1))
        assertEquals(2, dao.rows.value.size)
    }
}
