package io.opencell.platform.contacts

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun searchContacts(query: String, limit: Int = 50): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection, selection, selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "Unknown"
                val photo = cursor.getString(photoIndex)
                val hasPhone = cursor.getInt(hasPhoneIndex) > 0
                val phoneNumbers = if (hasPhone) getPhoneNumbers(id) else emptyList()
                contacts.add(Contact(id = id.toString(), displayName = name, photoUri = photo, phoneNumbers = phoneNumbers))
                count++
            }
        }
        return contacts
    }

    fun getAllContacts(limit: Int = 500): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val contactMap = mutableMapOf<String, ContactBuilder>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.TYPE
            ), null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex).toString()
                val name = cursor.getString(nameIndex) ?: "Unknown"
                val number = cursor.getString(numberIndex) ?: continue
                val photo = cursor.getString(photoIndex)
                val type = cursor.getInt(typeIndex)

                val builder = contactMap.getOrPut(id) { ContactBuilder(id, name, photo) }
                builder.phoneNumbers.add(PhoneNumber(number = number, type = mapPhoneType(type)))
            }

            contactMap.values.take(limit).forEach { builder ->
                contacts.add(Contact(id = builder.id, displayName = builder.name,
                    photoUri = builder.photoUri, phoneNumbers = builder.phoneNumbers))
            }
        }
        return contacts
    }

    private fun getPhoneNumbers(contactId: Long): List<PhoneNumber> {
        val phones = mutableListOf<PhoneNumber>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()), null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex) ?: continue
                val type = cursor.getInt(typeIndex)
                phones.add(PhoneNumber(number = number, type = mapPhoneType(type)))
            }
        }
        return phones
    }

    fun lookupByPhoneNumber(phoneNumber: String): Contact? {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
            arrayOf(phoneNumber), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return Contact(
                    id = cursor.getLong(0).toString(),
                    displayName = cursor.getString(1) ?: "Unknown",
                    photoUri = cursor.getString(2),
                    phoneNumbers = listOf(PhoneNumber(number = phoneNumber, type = "mobile"))
                )
            }
        }
        return null
    }

    private fun mapPhoneType(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax_work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax_home"
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "pager"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> "custom"
        else -> "other"
    }
}

@Serializable
data class Contact(val id: String, val displayName: String, val photoUri: String? = null, val phoneNumbers: List<PhoneNumber> = emptyList())

@Serializable
data class PhoneNumber(val number: String, val type: String = "mobile")

private class ContactBuilder(val id: String, val name: String, val photoUri: String?, val phoneNumbers: MutableList<PhoneNumber> = mutableListOf())
