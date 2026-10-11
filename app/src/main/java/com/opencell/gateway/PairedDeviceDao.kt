package com.opencell.gateway

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.opencell.policy.Actor
import com.opencell.policy.Scope
import kotlinx.coroutines.flow.Flow

/** A computer paired with the gateway. Only the SHA-256 of its token is stored. */
@Entity(tableName = "paired_devices", indices = [Index(value = ["tokenHash"], unique = true)])
data class PairedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tokenHash: String,
    val scopes: String,
    val createdAtMillis: Long,
    val lastSeenMillis: Long? = null,
    val revoked: Boolean = false,
) {
    fun toActor() = Actor.Device(id, name, Scope.parse(scopes))
}

@Dao
interface PairedDeviceDao {
    @Insert
    suspend fun insert(device: PairedDeviceEntity): Long

    @Update
    suspend fun update(device: PairedDeviceEntity)

    @Query("SELECT * FROM paired_devices WHERE tokenHash = :hash AND revoked = 0")
    suspend fun findActive(hash: String): PairedDeviceEntity?

    @Query("SELECT * FROM paired_devices WHERE id = :id")
    suspend fun find(id: Long): PairedDeviceEntity?

    @Query("UPDATE paired_devices SET lastSeenMillis = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("SELECT * FROM paired_devices WHERE revoked = 0 ORDER BY createdAtMillis DESC")
    fun observeActive(): Flow<List<PairedDeviceEntity>>
}
