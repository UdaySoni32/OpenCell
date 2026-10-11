package com.opencell.policy

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class Outcome { DONE, FAILED, DENIED, PENDING }

/** Every policy-checked action, kept so the user can see what the AI and paired computers did. */
@Entity(tableName = "audit_log")
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val actor: String,
    val action: ActionType,
    val target: String,
    val body: String?,
    val outcome: Outcome,
    val detail: String?,
)

enum class ApprovalStatus { PENDING, APPROVED, DENIED, EXPIRED }

/** An action waiting for the user to tap Approve / Deny. */
@Entity(tableName = "approvals")
data class ApprovalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val actorLabel: String,
    /** Paired device id, so the device's result can be reported back; null for AI. */
    val deviceId: Long?,
    val action: ActionType,
    val target: String,
    val body: String?,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val resultRef: String? = null,
)

@Dao
interface PolicyDao {
    @Insert
    suspend fun insertAudit(entry: AuditEntry): Long

    @Query("SELECT * FROM audit_log ORDER BY atMillis DESC LIMIT :limit")
    fun observeAudit(limit: Int = 200): Flow<List<AuditEntry>>

    /** Outbound actions that actually ran since [sinceMillis]; feeds the daily limit. */
    @Query("SELECT COUNT(*) FROM audit_log WHERE atMillis >= :sinceMillis AND outcome = 'DONE' AND action IN ('SEND_SMS', 'PLACE_CALL') AND actor != 'you'")
    suspend fun countOutboundSince(sinceMillis: Long): Int

    @Insert
    suspend fun insertApproval(approval: ApprovalEntity): Long

    @Update
    suspend fun updateApproval(approval: ApprovalEntity)

    @Query("SELECT * FROM approvals WHERE id = :id")
    suspend fun approval(id: Long): ApprovalEntity?

    @Query("SELECT * FROM approvals ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun recentApprovals(limit: Int = 100): List<ApprovalEntity>

    @Query("SELECT * FROM approvals WHERE status = 'PENDING' ORDER BY createdAtMillis DESC")
    fun observePending(): Flow<List<ApprovalEntity>>
}
