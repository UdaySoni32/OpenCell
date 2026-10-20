package com.opencell.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun requiresNotificationPermission(sdkInt: Int): Boolean = sdkInt >= 33

/** Older Android versions have no POST_NOTIFICATIONS runtime permission to check. */
internal fun notificationPermissionGranted(sdkInt: Int, isGranted: () -> Boolean): Boolean =
    !requiresNotificationPermission(sdkInt) || isGranted()

fun Context.hasNotificationPermission(): Boolean = notificationPermissionGranted(Build.VERSION.SDK_INT) {
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
