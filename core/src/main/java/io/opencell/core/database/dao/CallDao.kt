package io.opencell.core.database.dao

import androidx.room.*
import io.opencell.core.database.entity.CallEntity
import io.opencell.core.database.entity.CallEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startedAt DESC")
    fun getAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE deviceId = :deviceId ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentCalls(deviceId: String, limit: Int = 100): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :id")
    suspend fun getCall(id: String): CallEntity?

    @Query("SELECT * FROM calls WHERE id = :id")
    fun observeCall(id: String): Flow<CallEntity?>

    @Query("SELECT * FROM calls WHERE state IN ('RINGING', 'DIALING', 'ACTIVE', 'ON_HOLD') ORDER BY startedAt DESC")
    fun getActiveCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE state IN ('RINGING', 'DIALING', 'ACTIVE', 'ON_HOLD')")
    suspend fun getActiveCallsList(): List<CallEntity>

    @Query("SELECT * FROM calls WHERE fromNumber = :number OR toNumber = :number ORDER BY startedAt DESC LIMIT :limit")
    fun getCallsByNumber(number: String, limit: Int = 50): Flow<List<CallEntity>>

    /**
     * Find the most recent outbound call that was dialed to [number].
     * Returns calls in CREATED, DIALING, or RINGING state (i.e. not yet active/ended).
     * Used by InCallService to match a Telecom framework call to an existing record.
     */
    @Query("SELECT * FROM calls WHERE direction = 'OUTBOUND' AND toNumber = :number AND state IN ('CREATED', 'DIALING', 'RINGING') ORDER BY startedAt DESC LIMIT 1")
    suspend fun getRecentOutboundCallByNumber(number: String): CallEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCall(call: CallEntity)

    @Update
    suspend fun updateCall(call: CallEntity)

    @Query("UPDATE calls SET state = :state, answeredAt = :answeredAt, endedAt = :endedAt, durationMs = :durationMs WHERE id = :id")
    suspend fun updateCallState(id: String, state: String, answeredAt: Long? = null, endedAt: Long? = null, durationMs: Long = 0)

    @Delete
    suspend fun deleteCall(call: CallEntity)

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun deleteCallById(id: String)

    // Call events
    @Insert
    suspend fun insertCallEvent(event: CallEventEntity)

    @Query("SELECT * FROM call_events WHERE callId = :callId ORDER BY timestamp ASC")
    fun getCallEvents(callId: String): Flow<List<CallEventEntity>>

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallEvents(limit: Int = 100): Flow<List<CallEventEntity>>

    @Query("DELETE FROM call_events WHERE callId = :callId")
    suspend fun deleteCallEvents(callId: String)
}
