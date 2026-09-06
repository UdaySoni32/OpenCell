package com.example.opencell.data.repository

import com.example.opencell.data.local.db.dao.CallRecordDao
import com.example.opencell.data.local.db.entity.CallRecordEntity
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CallRepository {
    fun getAllCalls(): Flow<List<CallRecord>>
    fun getCallsByType(type: CallType): Flow<List<CallRecord>>
    suspend fun insertCall(call: CallRecord): Long
    suspend fun deleteCall(call: CallRecord)
    suspend fun clearAllCalls()
}

class CallRepositoryImpl(
    private val dao: CallRecordDao
) : CallRepository {

    override fun getAllCalls(): Flow<List<CallRecord>> {
        return dao.getAllCalls().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCallsByType(type: CallType): Flow<List<CallRecord>> {
        return dao.getCallsByType(type.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertCall(call: CallRecord): Long {
        return dao.insertCall(call.toEntity())
    }

    override suspend fun deleteCall(call: CallRecord) {
        dao.deleteCall(call.toEntity())
    }

    override suspend fun clearAllCalls() {
        dao.clearAllCalls()
    }

    private fun CallRecordEntity.toDomain(): CallRecord {
        val type = try {
            CallType.valueOf(callType)
        } catch (e: Exception) {
            CallType.INCOMING
        }
        return CallRecord(
            id = id,
            phoneNumber = phoneNumber,
            contactName = contactName,
            callType = type,
            timestamp = timestamp,
            durationSeconds = durationSeconds
        )
    }

    private fun CallRecord.toEntity(): CallRecordEntity {
        return CallRecordEntity(
            id = id,
            phoneNumber = phoneNumber,
            contactName = contactName,
            callType = callType.name,
            timestamp = timestamp,
            durationSeconds = durationSeconds
        )
    }
}
