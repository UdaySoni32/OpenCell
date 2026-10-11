package com.opencell.calllog

import org.junit.Assert.assertEquals
import org.junit.Test

class CallbackResolverTest {

    private class Lookups(
        val waId: Long? = null,
        val notifKey: String? = null,
        val installed: Boolean = true,
    ) : CallbackLookups {
        override fun whatsAppCallDataId(peerName: String, businessApp: Boolean) = waId
        override fun liveNotificationKey(packageName: String, peerName: String?) = notifKey
        override fun isInstalled(packageName: String) = installed
    }

    private fun entry(source: CallSource, name: String? = "Riya", number: String? = null) =
        CallEntry("id", source, CallKind.MISSED, name, number, 0, 0)

    @Test
    fun `sim dials the number`() {
        assertEquals(CallbackTarget.DialNumber("+911234"), CallbackResolver.resolve(entry(CallSource.SIM, number = "+911234"), Lookups()))
    }

    @Test
    fun `sim without number does nothing`() {
        assertEquals(CallbackTarget.None, CallbackResolver.resolve(entry(CallSource.SIM), Lookups()))
    }

    @Test
    fun `whatsapp prefers direct voip call`() {
        assertEquals(CallbackTarget.WhatsAppCall(7), CallbackResolver.resolve(entry(CallSource.WHATSAPP), Lookups(waId = 7, notifKey = "k")))
    }

    @Test
    fun `discord uses live notification then app launch`() {
        assertEquals(CallbackTarget.NotificationAction("k"), CallbackResolver.resolve(entry(CallSource.DISCORD), Lookups(notifKey = "k")))
        assertEquals(CallbackTarget.LaunchApp("com.discord"), CallbackResolver.resolve(entry(CallSource.DISCORD), Lookups()))
        assertEquals(CallbackTarget.None, CallbackResolver.resolve(entry(CallSource.DISCORD), Lookups(installed = false)))
    }
}
