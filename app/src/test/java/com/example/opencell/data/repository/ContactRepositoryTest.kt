package com.example.opencell.data.repository

import com.example.opencell.data.local.db.dao.ContactDao
import com.example.opencell.data.local.db.entity.ContactEntity
import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeContactDao : ContactDao {
    private val contacts = MutableStateFlow<List<ContactEntity>>(emptyList())

    override fun getAllContacts(): Flow<List<ContactEntity>> = contacts

    override suspend fun insertContact(contact: ContactEntity) {
        contacts.value = contacts.value.filterNot { it.id == contact.id } + contact
    }

    override suspend fun deleteContact(contact: ContactEntity) {
        contacts.value = contacts.value.filter { it.id != contact.id }
    }

    override suspend fun countContacts(): Int = contacts.value.size
}

class ContactRepositoryTest {

    private lateinit var fakeDao: FakeContactDao
    private lateinit var repository: ContactRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeContactDao()
        repository = ContactRepositoryImpl(fakeDao)
    }

    @Test
    fun addContact_thenGetAllContacts_returnsContact() = runTest {
        repository.addContact(
            Contact(id = "a1", name = "Alice", phoneNumber = "+15550100", carrierLabel = "T-Mobile")
        )

        val all = repository.getAllContacts().first()
        assertEquals(1, all.size)
        assertEquals("Alice", all[0].name)
        assertEquals("+15550100", all[0].phoneNumber)
        assertEquals("T-Mobile", all[0].carrierLabel)
    }

    @Test
    fun addContact_replacesExistingId() = runTest {
        repository.addContact(Contact(id = "a1", name = "Old Name", phoneNumber = "111"))
        repository.addContact(Contact(id = "a1", name = "New Name", phoneNumber = "111"))

        val all = repository.getAllContacts().first()
        assertEquals(1, all.size)
        assertEquals("New Name", all[0].name)
    }

    @Test
    fun deleteContact_removesOnlyThatContact() = runTest {
        repository.addContact(Contact(id = "a1", name = "Alice", phoneNumber = "111"))
        repository.addContact(Contact(id = "b2", name = "Bob", phoneNumber = "222"))

        repository.deleteContact(Contact(id = "a1", name = "Alice", phoneNumber = "111"))

        val all = repository.getAllContacts().first()
        assertEquals(1, all.size)
        assertEquals("Bob", all[0].name)
    }

    @Test
    fun isEmpty_reflectsDaoState() = runTest {
        assertTrue(repository.isEmpty())

        repository.addContact(Contact(id = "a1", name = "Alice", phoneNumber = "111"))
        assertFalse(repository.isEmpty())
    }
}
