package com.opencell.calllog

import com.opencell.calllog.notification.CallEventType
import com.opencell.calllog.notification.CallNotificationParser
import com.opencell.calllog.notification.NotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallNotificationParserTest {

    private fun snap(pkg: String, title: String?, text: String?, category: String? = null, ongoing: Boolean = false) =
        NotificationSnapshot("k", pkg, title, text, category, ongoing, 1_000L)

    @Test
    fun `whatsapp missed call uses title as peer`() {
        val e = CallNotificationParser.parse(snap("com.whatsapp", "Riya", "Missed voice call"))!!
        assertEquals(CallEventType.MISSED, e.type)
        assertEquals(CallSource.WHATSAPP, e.source)
        assertEquals("Riya", e.peerName)
    }

    @Test
    fun `generic missed title takes peer from text`() {
        val e = CallNotificationParser.parse(snap("org.telegram.messenger", "Missed call", "Arjun"))!!
        assertEquals(CallEventType.MISSED, e.type)
        assertEquals("Arjun", e.peerName)
    }

    @Test
    fun `missed count summary has no peer`() {
        val e = CallNotificationParser.parse(snap("com.whatsapp", "WhatsApp", "3 missed calls"))!!
        assertEquals(CallEventType.MISSED, e.type)
        assertNull(e.peerName)
    }

    @Test
    fun `call category ringing`() {
        val e = CallNotificationParser.parse(snap("com.discord", "Sam", "Incoming call", category = "call"))!!
        assertEquals(CallEventType.RINGING, e.type)
        assertEquals("Sam", e.peerName)
    }

    @Test
    fun `ongoing call with timer`() {
        val e = CallNotificationParser.parse(snap("com.whatsapp", "Riya", "Ongoing voice call 01:23", category = "call", ongoing = true))!!
        assertEquals(CallEventType.ONGOING, e.type)
    }

    @Test
    fun `missed_call category wins`() {
        val e = CallNotificationParser.parse(snap("org.thoughtcrime.securesms", "Mom", "Tap to call back", category = "missed_call"))!!
        assertEquals(CallEventType.MISSED, e.type)
        assertEquals("Mom", e.peerName)
    }

    @Test
    fun `chat message is ignored`() {
        assertNull(CallNotificationParser.parse(snap("com.whatsapp", "Riya", "see you at 5", category = "msg")))
    }

    @Test
    fun `system dialer notifications are ignored`() {
        assertNull(CallNotificationParser.parse(snap("com.google.android.dialer", "(555) 111-2222", "Ongoing call", category = "call", ongoing = true)))
    }

    @Test
    fun `unknown app needs call category`() {
        assertNull(CallNotificationParser.parse(snap("com.example.chat", "Bob", "Bob is calling you about the call")))
        val e = CallNotificationParser.parse(snap("com.example.chat", "Bob", "Incoming", category = "call"))!!
        assertEquals(CallSource.OTHER, e.source)
    }
}
