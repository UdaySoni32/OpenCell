package com.example.opencell.util

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Returns the primary IPv4 address of the active Wi-Fi or LAN interface (e.g., "192.168.1.100").
     * Returns null if disconnected or loopback-only.
     */
    fun getLocalIpAddress(context: Context? = null): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val activeInterfaces = ArrayList<NetworkInterface>()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                activeInterfaces.add(networkInterface)
            }

            // Prefer wlan / Wi-Fi or eth interfaces first
            val sortedInterfaces = activeInterfaces.sortedByDescending { ni ->
                val name = ni.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") -> 2
                    else -> 1
                }
            }

            for (networkInterface in sortedInterfaces) {
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (!hostAddress.isNullOrBlank() && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Suppress network inspection exceptions
        }
        return null
    }
}
