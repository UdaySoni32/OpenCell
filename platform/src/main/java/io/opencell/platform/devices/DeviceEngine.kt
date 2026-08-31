package io.opencell.platform.devices

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.*
import android.telephony.euicc.EuiccManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.core.crypto.CryptoUtils
import io.opencell.core.database.dao.DeviceDao
import io.opencell.core.database.entity.DeviceEntity
import io.opencell.core.database.entity.NetworkSnapshotEntity
import io.opencell.core.database.entity.SimInfoEntity
import io.opencell.core.model.NetworkInfo
import io.opencell.core.model.SimInfo
import io.opencell.platform.events.EventEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device Engine manages device identity, SIM info, network info, and capabilities.
 */
@Singleton
class DeviceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDao: DeviceDao,
    private val eventEngine: EventEngine
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val subscriptionManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    } else null

    private var localDeviceId: String? = null

    /**
     * Get or create the local device identity.
     */
    suspend fun getOrCreateLocalDevice(): DeviceEntity {
        val existingId = localDeviceId ?: getStoredDeviceId()
        if (existingId != null) {
            val existing = deviceDao.getDevice(existingId)
            if (existing != null) {
                localDeviceId = existingId
                deviceDao.updateOnlineStatus(existingId, true)
                return existing
            }
        }

        // Create new device
        val deviceId = CryptoUtils.generateId("dev")
        val device = DeviceEntity(
            id = deviceId,
            name = "${Build.MANUFACTURER} ${Build.MODEL}",
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            isActive = true,
            isOnline = true
        )

        deviceDao.upsertDevice(device)
        localDeviceId = deviceId

        // Store device ID
        context.getSharedPreferences("opencell", Context.MODE_PRIVATE)
            .edit()
            .putString("device_id", deviceId)
            .apply()

        eventEngine.emit("device.connected", deviceId, emptyMap())

        return device
    }

    fun getLocalDeviceIdSync(): String {
        return localDeviceId ?: getStoredDeviceId() ?: "unknown"
    }

    suspend fun getLocalDeviceId(): String {
        return getOrCreateLocalDevice().id
    }

    /**
     * Get SIM information for all active subscriptions.
     */
    fun getSimInfo(): List<SimInfo> {
        val result = mutableListOf<SimInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sm = subscriptionManager ?: return result

            try {
                val subscriptions = sm.activeSubscriptionInfoList ?: return result
                for (sub in subscriptions) {
                    result.add(SimInfo(
                        subscriptionId = sub.subscriptionId,
                        slotIndex = sub.simSlotIndex,
                        carrierName = sub.carrierName?.toString() ?: "Unknown",
                        displayName = sub.displayName?.toString() ?: "Unknown",
                        number = sub.number ?: telephonyManager?.line1Number,
                        mcc = sub.mccString,
                        mnc = sub.mncString,
                        countryCode = sub.countryIso,
                        isActive = sub.simSlotIndex >= 0,
                        isEmbedded = sub.isEmbedded
                    ))
                }
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }

        return result
    }

    /**
     * Get network information.
     */
    fun getNetworkInfo(): NetworkInfo {
        val cm = connectivityManager ?: return defaultNetworkInfo()
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val type = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            else -> "NONE"
        }

        return NetworkInfo(
            type = type,
            name = linkProps?.interfaceName,
            isAvailable = activeNetwork != null,
            isConnected = caps != null,
            isRoaming = telephonyManager?.isNetworkRoaming ?: false,
            signalStrength = getSignalStrength(),
            signalLevel = getSignalLevel()
        )
    }

    /**
     * Get signal strength information.
     */
    private fun getSignalStrength(): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val ss = telephonyManager?.signalStrength
                ss?.cellSignalStrengths?.firstOrNull()?.dbm
            } else {
                telephonyManager?.signalStrength?.cdmaDbm
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun getSignalLevel(): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ss = telephonyManager?.signalStrength
                ss?.cellSignalStrengths?.firstOrNull()?.level
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Store network snapshot for history.
     */
    suspend fun storeNetworkSnapshot(deviceId: String) {
        val networkInfo = getNetworkInfo()
        deviceDao.insertNetworkSnapshot(NetworkSnapshotEntity(
            deviceId = deviceId,
            type = networkInfo.type,
            name = networkInfo.name,
            isAvailable = networkInfo.isAvailable,
            isConnected = networkInfo.isConnected,
            isRoaming = networkInfo.isRoaming,
            signalStrength = networkInfo.signalStrength,
            signalLevel = networkInfo.signalLevel,
            asuLevel = null,
            dbmLevel = networkInfo.dbmLevel
        ))
    }

    /**
     * Check if eSIM is supported.
     */
    fun isEsimSupported(): Boolean {
        val euiccManager = context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
        return euiccManager?.isEnabled == true
    }

    private fun getStoredDeviceId(): String? {
        return context.getSharedPreferences("opencell", Context.MODE_PRIVATE)
            .getString("device_id", null)
    }

    private fun defaultNetworkInfo() = NetworkInfo(
        type = "UNKNOWN",
        name = null,
        isAvailable = false,
        isConnected = false,
        isRoaming = false
    )
}
