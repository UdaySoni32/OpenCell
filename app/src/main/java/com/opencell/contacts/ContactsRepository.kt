package com.opencell.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import com.opencell.data.observeQuery
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

data class Contact(val id: Long, val name: String, val number: String)

class ContactsRepository(private val resolver: ContentResolver) {

    private val nameCache = ConcurrentHashMap<String, String>()

    fun observeAll(): Flow<List<Contact>> =
        resolver.observeQuery(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, emptyList()) { queryAll() }

    /** Contact name for a phone number, or null. Blocking: call off the main thread. */
    fun nameFor(number: String): String? {
        if (number.isBlank()) return null
        nameCache[number]?.let { return it }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val name = runCatching {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        name?.let { nameCache[number] = it }
        return name
    }

    private fun queryAll(): List<Contact> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val out = mutableListOf<Contact>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) out += Contact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
        }
        return out.distinctBy { it.id to it.number.filter(Char::isDigit) }
    }
}
