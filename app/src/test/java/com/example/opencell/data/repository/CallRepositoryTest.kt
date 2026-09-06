package com.example.opencell.data.repository

import com.example.opencell.data.local.db.dao.CallRecordDao
import com.example.opencell.data.local.db.entity.CallRecordEntity
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeCallRecordDao : CallRecordDao {
    private val records = MutableStateFlow<List<CallRecordEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllCalls(): Flow<List<CallRecordEntity>> = records

    override fun getCallsByType(type: String): Flow<List<CallRecordEntity>> {
        return records.map { list -> list.filter { it.callType == type } }
    }

    override suspend fun insertCall(call: CallRecordEntity): Long {
        val id = if (call.id == 0L) nextId++ else call.id
        val entity = call.copy(id = id)
        records.value = records.value + entity
        return id
    }

    override suspend fun deleteCall(call: CallRecordEntity) {
        records.value = records.value.filter { it.id != call.id }
    }

    override suspend fun clearAllCalls() {
        records.value = emptyList()
    }
}

class CallRepositoryTest {

    private lateinit var fakeDao: FakeCallRecordDao
    private lateinit var repository: CallRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeCallRecordDao()
        repository = CallRepositoryImpl(fakeDao)
    }

    @Test
    fun insertAndGetAllCalls_returnsInsertedCall() = runTest {
        val call = CallRecord(
            phoneNumber = "+15550100",
            contactName = "Test Contact",
            callType = CallType.OUTGOING,
            durationSeconds = 60
        )

        repository.insertCall(call)
        val allCalls = repository.getAllCalls().first()

        assertEquals(1, allCalls.size)
        assertEquals("+15550100", allCalls[0].phoneNumber)
        assertEquals("Test Contact", allCalls[0].contactName)
        assertEquals(CallType.OUTGOING, allCalls[0].callType)
    }

    @Test
    fun getCallsByType_filtersCorrectly() = runTest {
        repository.insertCall(CallRecord(phoneNumber = "111", callType = CallType.INCOMING))
        repository.insertCall(CallRecord(phoneNumber = "222", callType = CallType.MISSED))
        repository.insertCall(CallRecord(phoneNumber = "333", callType = CallType.INCOMING))

        val incomingCalls = repository.getCallsByType(CallType.INCOMING).first()
        val missedCalls = repository.getCallsByType(CallType.MISSED).first()

        assertEquals(2, incomingCalls.size)
        assertEquals(1, missedCalls.size)
        assertEquals("222", missedCalls[0].phoneNumber)
    }

    @Test
    fun clearAllCalls_emptiesRepository() = runTest {
        repository.insertCall(CallRecord(phoneNumber = "111", callType = CallType.INCOMING))
        repository.insertCall(CallRecord(phoneNumber = "222", callType = CallType.OUTGOING))

        repository.clearAllCalls()
        val allCalls = repository.getAllCalls().first()

        assertEquals(0, allCalls.size)
    }
}
