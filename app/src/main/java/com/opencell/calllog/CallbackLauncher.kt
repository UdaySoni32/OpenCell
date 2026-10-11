package com.opencell.calllog

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import com.opencell.calllog.notification.CallNotificationCache
import com.opencell.telecom.CallPlacer

/** Executes a [CallbackTarget] on the device. */
class CallbackLauncher(
    private val context: Context,
    private val cache: CallNotificationCache,
    private val callPlacer: CallPlacer,
) : CallbackLookups {

    /** Returns false when nothing could be launched. */
    fun callBack(entry: CallEntry): Boolean = when (val target = CallbackResolver.resolve(entry, this)) {
        is CallbackTarget.DialNumber -> callPlacer.placeCall(target.number)
        is CallbackTarget.WhatsAppCall -> startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, target.dataId),
                if (entry.source == CallSource.WHATSAPP_BUSINESS) WA_BUSINESS_VOIP_MIME else WA_VOIP_MIME,
            ).setPackage(entry.packageName),
        )
        is CallbackTarget.NotificationAction -> sendNotificationAction(target.notificationKey)
        is CallbackTarget.LaunchApp -> context.packageManager.getLaunchIntentForPackage(target.packageName)
            ?.let { startActivity(it) } ?: false
        CallbackTarget.None -> false
    }

    override fun whatsAppCallDataId(peerName: String, businessApp: Boolean): Long? = runCatching {
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.DISPLAY_NAME} = ?",
            arrayOf(if (businessApp) WA_BUSINESS_VOIP_MIME else WA_VOIP_MIME, peerName),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()

    override fun liveNotificationKey(packageName: String, peerName: String?): String? = cache.findKey(packageName, peerName)

    @Suppress("DEPRECATION") // the flags overload needs API 33; minSdk is 30
    override fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    private fun startActivity(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Log.w(TAG, "callback launch failed", it) }.isSuccess

    private fun sendNotificationAction(key: String): Boolean {
        val action = cache.action(key) ?: return false
        return runCatching {
            // Android 14+ only lets the target open an activity if we (the foreground sender) opt in.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            action.send(context, 0, null, null, null, null, options)
        }.onFailure { if (it is PendingIntent.CanceledException) cache.remove(key) }.isSuccess
    }

    private companion object {
        const val TAG = "CallbackLauncher"
        const val WA_VOIP_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        const val WA_BUSINESS_VOIP_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
    }
}
