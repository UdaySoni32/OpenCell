package io.opencell.core.database.dao

import androidx.room.*
import io.opencell.core.database.entity.DeviceEntity
import io.opencell.core.database.entity.SimInfoEntity
import io.opencell.core.database.entity.NetworkSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE isActive = 1")
    fun getActiveDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getDevice(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE id = :id")
    fun observeDevice(id: String): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: DeviceEntity)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("UPDATE devices SET isOnline = :online, lastSeenAt = :timestamp WHERE id = :id")
    suspend fun updateOnlineStatus(id: String, online: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteDevice(id: String)

    // SIM operations
    @Query("SELECT * FROM sim_info WHERE deviceId = :deviceId")
    fun getSimInfoForDevice(deviceId: String): Flow<List<SimInfoEntity>>

    @Query("SELECT * FROM sim_info WHERE subscriptionId = :subscriptionId")
    suspend fun getSimBySubscriptionId(subscriptionId: Int): SimInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSimInfo(sim: SimInfoEntity)

    @Delete
    suspend fun deleteSimInfo(sim: SimInfoEntity)

    @Query("DELETE FROM sim_info WHERE deviceId = :deviceId")
    suspend fun deleteAllSimInfoForDevice(deviceId: String)

    // Network snapshots
    @Insert
    suspend fun insertNetworkSnapshot(snapshot: NetworkSnapshotEntity)

    @Query("SELECT * FROM network_snapshots WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestNetworkSnapshot(deviceId: String): NetworkSnapshotEntity?

    @Query("SELECT * FROM network_snapshots WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentNetworkSnapshots(deviceId: String, limit: Int = 50): Flow<List<NetworkSnapshotEntity>>
}
