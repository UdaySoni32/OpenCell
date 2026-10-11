package com.opencell.calllog

/** How tapping a call-list row calls the person back. */
sealed interface CallbackTarget {
    data class DialNumber(val number: String) : CallbackTarget
    /** WhatsApp exposes a per-contact "voice call" row in the contacts provider; viewing it starts the call. */
    data class WhatsAppCall(val dataId: Long) : CallbackTarget
    /** The original notification's tap action, which usually lands in the right chat. */
    data class NotificationAction(val notificationKey: String) : CallbackTarget
    data class LaunchApp(val packageName: String) : CallbackTarget
    data object None : CallbackTarget
}

/** Lookups the resolver needs; implemented with ContactsContract + the notification cache on device. */
interface CallbackLookups {
    fun whatsAppCallDataId(peerName: String, businessApp: Boolean): Long?
    fun liveNotificationKey(packageName: String, peerName: String?): String?
    fun isInstalled(packageName: String): Boolean
}

object CallbackResolver {
    fun resolve(entry: CallEntry, lookups: CallbackLookups): CallbackTarget {
        if (entry.source == CallSource.SIM) {
            return entry.peerNumber?.let { CallbackTarget.DialNumber(it) } ?: CallbackTarget.None
        }
        val pkg = entry.packageName ?: return CallbackTarget.None
        val isWhatsApp = entry.source == CallSource.WHATSAPP || entry.source == CallSource.WHATSAPP_BUSINESS
        if (isWhatsApp && entry.peerName != null) {
            lookups.whatsAppCallDataId(entry.peerName, entry.source == CallSource.WHATSAPP_BUSINESS)
                ?.let { return CallbackTarget.WhatsAppCall(it) }
        }
        lookups.liveNotificationKey(pkg, entry.peerName)?.let { return CallbackTarget.NotificationAction(it) }
        return if (lookups.isInstalled(pkg)) CallbackTarget.LaunchApp(pkg) else CallbackTarget.None
    }
}
