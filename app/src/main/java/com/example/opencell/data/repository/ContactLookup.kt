package com.example.opencell.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.opencell.ui.contacts.ContactStore

object ContactLookup {

    /**
     * Resolves a phone number to a contact display name.
     * Looks up in:
     * 1. ContactStore (Room-backed memory cache)
     * 2. ContactsContract (System contacts provider if permission granted)
     * 3. Fallback sample mapping (e.g. Alice Smith "+15551234567", Bob Johnson "+15559876543")
     */
    fun resolveContactName(context: Context?, phoneNumber: String): String? {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) return null

        val digits = extractDigits(cleanNumber)

        // 1. Search in ContactStore
        val storeMatch = ContactStore.contacts.value.find { contact ->
            val contactDigits = extractDigits(contact.phoneNumber)
            numbersMatch(cleanNumber, contact.phoneNumber, digits, contactDigits)
        }
        if (storeMatch != null) return storeMatch.name

        // 2. Search in system ContactsContract if context & permission available
        if (context != null && hasReadContactsPermission(context)) {
            val systemName = lookupSystemContact(context, cleanNumber)
            if (!systemName.isNullOrBlank()) return systemName
        }

        // 3. Check fallback sample numbers
        return FALLBACK_MAPPING[digits] ?: FALLBACK_MAPPING[cleanNumber]
    }

    private fun lookupSystemContact(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        return it.getString(nameIdx)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun hasReadContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun numbersMatch(num1: String, num2: String, digits1: String, digits2: String): Boolean {
        if (num1 == num2) return true
        if (digits1.isNotEmpty() && digits1 == digits2) return true
        if (digits1.length >= 7 && digits2.length >= 7 &&
            (digits1.endsWith(digits2.takeLast(7)) || digits2.endsWith(digits1.takeLast(7)))
        ) {
            return true
        }
        return false
    }

    fun extractDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    private val FALLBACK_MAPPING = mapOf(
        "15551234567" to "Alice Smith",
        "5551234567" to "Alice Smith",
        "15550123456" to "Alice Smith",
        "15559876543" to "Bob Johnson",
        "5559876543" to "Bob Johnson",
        "15550198765" to "Bob Johnson",
        "911" to "Emergency",
        "1911" to "Emergency"
    )
}
