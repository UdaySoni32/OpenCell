package com.example.opencell.data.repository

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
        dao.insertContact(contact.toEntity())
    }

    override suspend fun deleteContact(contact: Contact) {
        dao.deleteContact(contact.toEntity())
    }

    override suspend fun isEmpty(): Boolean {
        return dao.countContacts() == 0
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
