package io.opencell.app.call

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract

/**
 * Resolves a phone number to a contact display name using the system contacts provider.
 */
object CallerLookup {

    /**
     * Look up the contact name for [phoneNumber].
     * Returns the contact's display name, or null if not found.
     */
    fun lookupContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Format a phone number for display.
     * Simple formatting — groups digits for readability.
     */
    fun formatPhoneNumber(number: String): String {
        val digits = number.filter { it.isDigit() || it == '+' }
        if (digits.length <= 4) return digits
        if (digits.startsWith("+")) {
            val country = digits.substring(0, minOf(3, digits.length))
            val rest = digits.substring(minOf(3, digits.length))
            return "$country ${formatLocalNumber(rest)}"
        }
        return formatLocalNumber(digits)
    }

    private fun formatLocalNumber(digits: String): String {
        return when (digits.length) {
            10 -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
            else -> digits
        }
    }
}
