package com.opencell.calllog

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A call seen in another app (WhatsApp, Discord, …), stored because Android keeps no log of them. */
@Entity(tableName = "app_calls")
data class AppCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val kind: CallKind,
    val peerName: String?,
    val startedAtMillis: Long,
    val answeredAtMillis: Long? = null,
    val durationSeconds: Long = 0,
    val notificationKey: String? = null,
) {
    fun toEntry(): CallEntry {
        val source = CallSource.fromPackage(packageName)
        return CallEntry(
            id = "app-$id",
            source = source,
            kind = kind,
            peerName = peerName,
            peerNumber = null,
            timestampMillis = startedAtMillis,
            durationSeconds = durationSeconds,
            packageName = packageName,
        )
    }
}

@Dao
interface AppCallDao {
    @Query("SELECT * FROM app_calls ORDER BY startedAtMillis DESC LIMIT 500")
    fun observeAll(): Flow<List<AppCallEntity>>

    @Query(
        """SELECT * FROM app_calls WHERE packageName = :packageName
           AND ((:peerName IS NULL AND peerName IS NULL) OR peerName = :peerName)
           AND startedAtMillis >= :sinceMillis ORDER BY startedAtMillis DESC LIMIT 1""",
    )
    suspend fun findRecent(packageName: String, peerName: String?, sinceMillis: Long): AppCallEntity?

    @Query("SELECT * FROM app_calls WHERE id = :id")
    suspend fun findById(id: Long): AppCallEntity?

    @Insert
    suspend fun insert(entity: AppCallEntity): Long

    @Update
    suspend fun update(entity: AppCallEntity)
}
