package com.opencell

import com.opencell.calllog.CallEntry
import com.opencell.calllog.CallKind
import com.opencell.calllog.CallSource
import com.opencell.calllog.mergeCallLogs
import com.opencell.contacts.Contact
import com.opencell.sms.SmsMessage
import com.opencell.sms.SmsRepository
import com.opencell.sms.SmsStatus
import com.opencell.ui.calls.CallFilter
import com.opencell.ui.calls.applyFilter
import com.opencell.ui.contacts.filterContacts
import org.junit.Assert.assertEquals
import org.junit.Test

class ListLogicTest {

    private fun call(id: String, source: CallSource, kind: CallKind, t: Long) = CallEntry(id, source, kind, null, null, t, 0)

    @Test
    fun `merge interleaves sim and app calls newest first`() {
        val merged = mergeCallLogs(
            listOf(call("s1", CallSource.SIM, CallKind.INCOMING, 10), call("s2", CallSource.SIM, CallKind.MISSED, 30)),
            listOf(call("a1", CallSource.WHATSAPP, CallKind.MISSED, 20)),
        )
        assertEquals(listOf("s2", "a1", "s1"), merged.map { it.id })
    }

    @Test
    fun `filters`() {
        val calls = listOf(
            call("s", CallSource.SIM, CallKind.MISSED, 1),
            call("a", CallSource.DISCORD, CallKind.INCOMING, 2),
        )
        assertEquals(listOf("s"), applyFilter(calls, CallFilter.MISSED).map { it.id })
        assertEquals(listOf("s"), applyFilter(calls, CallFilter.PHONE).map { it.id })
        assertEquals(listOf("a"), applyFilter(calls, CallFilter.APPS).map { it.id })
    }

    @Test
    fun `threads group newest message and count unread incoming`() {
        fun m(id: Long, thread: Long, t: Long, incoming: Boolean, read: Boolean) =
            SmsMessage(id, thread, "+91$thread", "body$id", t, incoming, if (incoming) SmsStatus.RECEIVED else SmsStatus.SENT, read)
        val threads = SmsRepository.groupIntoThreads(
            listOf(m(5, 2, 50, true, false), m(4, 1, 40, false, true), m(3, 2, 30, true, false), m(1, 1, 10, true, false)),
        )
        assertEquals(listOf(2L, 1L), threads.map { it.threadId })
        assertEquals("body5", threads[0].snippet)
        assertEquals(2, threads[0].unreadCount)
        assertEquals(1, threads[1].unreadCount)
    }

    @Test
    fun `contact search matches name or digits`() {
        val contacts = listOf(Contact(1, "Riya Sharma", "+91 98765 43210"), Contact(2, "Arjun", "040-1234"))
        assertEquals(listOf(1L), filterContacts(contacts, "riya").map { it.id })
        assertEquals(listOf(1L), filterContacts(contacts, "98765").map { it.id })
        assertEquals(2, filterContacts(contacts, " ").size)
    }
}
