package com.example.opencell.data.repository

import com.example.opencell.data.local.db.dao.MessageRecordDao
import com.example.opencell.data.local.db.entity.MessageRecordEntity
import com.example.opencell.domain.model.MessageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeMessageRecordDao : MessageRecordDao {
    private val records = MutableStateFlow<List<MessageRecordEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllMessages(): Flow<List<MessageRecordEntity>> = records

    override fun getConversationSummaries(): Flow<List<MessageRecordEntity>> {
        return records.map { list ->
            list.groupBy { it.address }.mapNotNull { (_, group) -> group.maxByOrNull { it.timestamp } }
        }
    }

    override fun getMessagesForAddress(address: String): Flow<List<MessageRecordEntity>> {
        return records.map { list -> list.filter { it.address == address } }
    }

    override suspend fun getMessageById(id: Long): MessageRecordEntity? {
        return records.value.find { it.id == id }
    }

    override suspend fun insertMessage(message: MessageRecordEntity): Long {
        val id = if (message.id == 0L) nextId++ else message.id
        val entity = message.copy(id = id)
        records.value = records.value + entity
        return id
    }

    override suspend fun updateMessageStatus(id: Long, status: String) {
        records.value = records.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    override suspend fun deleteMessage(message: MessageRecordEntity) {
        records.value = records.value.filter { it.id != message.id }
    }

    override suspend fun clearAllMessages() {
        records.value = emptyList()
    }
}

class MessageRepositoryTest {

    private lateinit var fakeDao: FakeMessageRecordDao
    private lateinit var repository: MessageRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeMessageRecordDao()
        repository = MessageRepositoryImpl(fakeDao)
    }

    @Test
    fun insertAndGetAllMessages_returnsInsertedMessage() = runTest {
        val message = MessageRecord(
            address = "555-0199",
            contactName = "NOC",
            body = "Signal test ping",
            isIncoming = true
        )

        repository.insertMessage(message)
        val messages = repository.getAllMessages().first()

        assertEquals(1, messages.size)
        assertEquals("555-0199", messages[0].address)
        assertEquals("Signal test ping", messages[0].body)
    }

    @Test
    fun getMessagesForAddress_filtersByAddress() = runTest {
        repository.insertMessage(MessageRecord(address = "111", body = "Hello 1"))
        repository.insertMessage(MessageRecord(address = "222", body = "Hello 2"))
        repository.insertMessage(MessageRecord(address = "111", body = "Hello 1 again"))

        val messages111 = repository.getMessagesForAddress("111").first()

        assertEquals(2, messages111.size)
        assertEquals("Hello 1", messages111[0].body)
        assertEquals("Hello 1 again", messages111[1].body)
    }

    @Test
    fun clearAllMessages_removesAllRecords() = runTest {
        repository.insertMessage(MessageRecord(address = "111", body = "Hello 1"))
        repository.clearAllMessages()

        val messages = repository.getAllMessages().first()
        assertEquals(0, messages.size)
    }
}
