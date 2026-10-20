package com.opencell.sms

import org.junit.Assert.*
import org.junit.Test

class MultipartSmsStateTest {
    @Test fun `last segment arriving first is not sent until every segment succeeds`() {
        var state = MultipartSmsState.pending(3).record(2, false, 'S')
        assertFalse(state.sentComplete)
        state = state.record(0, false, 'S').record(1, false, 'S')
        assertTrue(state.sentComplete)
        assertFalse(state.complete) // Delivery reports are still outstanding.
    }

    @Test fun `duplicates cannot stand in for missing segments`() {
        val state = MultipartSmsState.pending(2).record(1, false, 'S').record(1, false, 'S')
            .record(1, true, 'S').record(1, true, 'S')
        assertFalse(state.sentComplete)
        assertFalse(state.deliveredComplete)
        assertFalse(state.complete)
    }

    @Test fun `send failure cannot be overwritten by same or last segment success`() {
        val state = MultipartSmsState.pending(2).record(0, false, 'F')
            .record(0, false, 'S').record(1, false, 'S')
        assertTrue(state.failed)
        assertTrue(state.complete)
        assertFalse(state.sentComplete)
    }

    @Test fun `delivery failure survives subsequent sent and delivery successes`() {
        val state = MultipartSmsState.pending(2).record(0, true, 'F')
            .record(0, true, 'S').record(1, true, 'S')
            .record(0, false, 'S').record(1, false, 'S')
        assertTrue(state.failed)
        assertFalse(state.deliveredComplete)
    }

    @Test fun `delivery before sent works and persisted state survives recreation`() {
        val before = MultipartSmsState.pending(2).record(1, true, 'S').record(0, true, 'S')
            .record(1, false, 'S')
        val restored = MultipartSmsState.decode(before.encode())!!
        assertEquals(before, restored)
        assertFalse(restored.complete)
        assertTrue(restored.record(0, false, 'S').complete)
    }

    @Test fun `temporary and missing delivery reports are not delivered`() {
        assertEquals('P', smsDeliveryResult(true, 0x20))
        assertEquals('P', smsDeliveryResult(true, 0x3f))
        assertEquals('P', smsDeliveryResult(true, null))
        assertEquals('P', smsDeliveryResult(true, -1))
        assertEquals('F', smsDeliveryResult(true, 0x40))
        assertEquals('F', smsDeliveryResult(false, 0))
        assertEquals('S', smsDeliveryResult(true, 0))
        val state = MultipartSmsState.pending(1).record(0, true, 'S').record(0, true, 'P')
        assertTrue(state.deliveredComplete)
    }

    @Test fun `invalid callback index and corrupt persisted state are ignored`() {
        val state = MultipartSmsState.pending(2)
        assertEquals(state, state.record(2, false, 'S'))
        assertEquals(state, state.record(-1, true, 'F'))
        assertNull(MultipartSmsState.decode("SS|S"))
        assertNull(MultipartSmsState.decode("|"))
        assertNull(MultipartSmsState.decode("XX|PP"))
    }

    @Test fun `send and setup exceptions mark inserted row failed and return null`() {
        for (exception in listOf(SecurityException("send denied"), IllegalArgumentException("setup failed"))) {
            var failed = false
            assertNull(sendSmsOrNull(42, send = { throw exception }, markFailed = { failed = true }))
            assertTrue(failed)
        }
    }

    @Test fun `successful send returns inserted id and failed provider update still returns null`() {
        assertEquals(42L, sendSmsOrNull(42, send = {}, markFailed = { fail("unexpected failure") }))
        assertNull(sendSmsOrNull(42, send = { error("send failed") }, markFailed = { error("provider unavailable") }))
    }
}
