package com.example.opencell.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.opencell.data.local.db.dao.ContactDao
import com.example.opencell.data.local.db.entity.ContactEntity
import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ContactRepository {
    fun getAllContacts(): Flow<List<Contact>>
    suspend fun addContact(contact: Contact)
    suspend fun deleteContact(contact: Contact)
    suspend fun isEmpty(): Boolean
    suspend fun loadContactsFromSystem(context: Context): List<Contact>
}

class ContactRepositoryImpl(
    private val dao: ContactDao
) : ContactRepository {

    override fun getAllContacts(): Flow<List<Contact>> {
        return dao.getAllContacts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addContact(contact: Contact) {
        val cleanedContact = contact.copy(name = contact.name.trim())
        dao.insertContact(cleanedContact.toEntity())
    }

    override suspend fun deleteContact(contact: Contact) {
        dao.deleteContact(contact.toEntity())
    }

    override suspend fun isEmpty(): Boolean {
        return dao.countContacts() == 0
    }

    override suspend fun loadContactsFromSystem(context: Context): List<Contact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val systemContacts = mutableListOf<Contact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var index = 1
                while (it.moveToNext()) {
                    val rawName = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val rawNumber = if (numIdx >= 0) it.getString(numIdx) else null
                    if (!rawName.isNullOrBlank() && !rawNumber.isNullOrBlank()) {
                        val cleanedName = rawName.trim()
                        val normalizedPhoneNumber = rawNumber.replace(Regex("[^0-9]"), "").takeLast(10)
                        if (normalizedPhoneNumber.isNotBlank() || cleanedName.isNotBlank()) {
                            val isDuplicate = systemContacts.any { existing ->
                                val existingNormPhone = existing.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
                                val existingNameLower = existing.name.trim().lowercase()
                                (normalizedPhoneNumber.isNotEmpty() && existingNormPhone == normalizedPhoneNumber) ||
                                        (existingNameLower == cleanedName.lowercase() && existingNormPhone == normalizedPhoneNumber)
                            }
                            if (!isDuplicate) {
                                systemContacts.add(
                                    Contact(
                                        id = "sys_$index",
                                        name = cleanedName,
                                        phoneNumber = rawNumber,
                                        carrierLabel = "Device Contact"
                                    )
                                )
                                index++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return systemContacts
    }

    private fun ContactEntity.toDomain(): Contact {
        return Contact(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            email = email,
            avatarColorHex = avatarColorHex,
            carrierLabel = carrierLabel
        )
    }

    private fun Contact.toEntity(): ContactEntity {
        return ContactEntity(
            id = id,
            name = name,
            phoneNumber = phoneNumber,
            email = email,
            avatarColorHex = avatarColorHex,
            carrierLabel = carrierLabel
        )
    }
}
