package com.example.opencell.gateway

import com.example.opencell.domain.model.TelephonyEvent
import com.example.opencell.domain.model.WebhookRecord
import com.example.opencell.gateway.event.EventEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventEngineTest {

    @Test
    fun testEmitCallCreatedEvent() = runTest {
        val eventEngine = EventEngine(externalScope = this)

        eventEngine.emitCallCreated(
            callId = "call_123",
            phoneNumber = "+15550199",
            type = "OUTGOING"
        )

        val event: TelephonyEvent = eventEngine.events.first()
        assertEquals(WebhookRecord.EVENT_CALL_CREATED, event.eventType)
        assertEquals("call_123", event.payload["callId"])
        assertEquals("+15550199", event.payload["phoneNumber"])
        assertEquals("OUTGOING", event.payload["callType"])
    }

    @Test
    fun testEmitMessageCreatedEvent() = runTest {
        val eventEngine = EventEngine(externalScope = this)

        eventEngine.emitMessageCreated(
            messageId = "msg_456",
            address = "+15550123",
            body = "Hello from Gateway API",
            isIncoming = false
        )

        val event: TelephonyEvent = eventEngine.events.first()
        assertEquals(WebhookRecord.EVENT_MESSAGE_CREATED, event.eventType)
        assertEquals("msg_456", event.payload["messageId"])
        assertEquals("+15550123", event.payload["address"])
        assertEquals("Hello from Gateway API", event.payload["body"])
        assertEquals("false", event.payload["isIncoming"])
    }
}
