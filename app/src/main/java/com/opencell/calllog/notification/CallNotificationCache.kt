package com.opencell.calllog.notification

import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap

/** Tap actions of call notifications that are still showing; they die with the notification. */
class CallNotificationCache {
    private data class Item(val packageName: String, val peerName: String?, val action: PendingIntent)

    private val items = ConcurrentHashMap<String, Item>()

    fun put(key: String, packageName: String, peerName: String?, action: PendingIntent) {
        items[key] = Item(packageName, peerName, action)
    }

    fun remove(key: String) {
        items.remove(key)
    }

    fun findKey(packageName: String, peerName: String?): String? =
        items.entries.firstOrNull { it.value.packageName == packageName && it.value.peerName == peerName }?.key
            ?: items.entries.firstOrNull { it.value.packageName == packageName && peerName == null }?.key

    fun action(key: String): PendingIntent? = items[key]?.action
}
