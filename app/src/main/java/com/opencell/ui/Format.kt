package com.opencell.ui

import android.text.format.DateUtils

fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** Avatar letter: first letter of a name, "#" for bare numbers. */
fun initialOf(name: String): String = name.firstOrNull { it.isLetter() }?.uppercase() ?: "#"

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
