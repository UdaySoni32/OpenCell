package io.opencell.platform.events

import io.opencell.core.crypto.CryptoUtils
import io.opencell.core.database.dao.EventDao
import io.opencell.core.database.entity.EventEntity
import io.opencell.core.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventEngine @Inject constructor(
    private val eventDao: EventDao
) {
    private val _events = MutableSharedFlow<Event>(
        replay = 100,
        extraBufferCapacity = 500
    )
    val events = _events.asSharedFlow()

    private var sequenceCounters = mutableMapOf<String, Long>()

    suspend fun emit(
        name: String,
        deviceId: String,
        data: Map<String, JsonElement?> = emptyMap(),
        requestId: String? = null
    ) {
        val seq = (sequenceCounters[deviceId] ?: 0L) + 1
        sequenceCounters[deviceId] = seq

        val event = Event(
            id = CryptoUtils.generateId("evt"),
            name = name,
            deviceId = deviceId,
            data = data,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = seq,
            requestId = requestId
        )

        val dataJson = JsonObject(data.mapValues { (_, v) -> v ?: JsonNull })

        eventDao.insertEvent(EventEntity(
            id = event.id,
            name = event.name,
            deviceId = event.deviceId,
            data = Json.encodeToString(JsonElement.serializer(), dataJson),
            timestamp = event.timestamp,
            sequenceNumber = event.sequenceNumber,
            requestId = event.requestId
        ))

        _events.emit(event)
    }

    fun getRecentEvents(limit: Int = 100): Flow<List<EventEntity>> {
        return eventDao.getRecentEvents(limit)
    }

    fun getEventsForDevice(deviceId: String, limit: Int = 100): Flow<List<EventEntity>> {
        return eventDao.getEventsForDevice(deviceId, limit)
    }

    fun getEventsSince(deviceId: String, sinceSeq: Long): Flow<List<EventEntity>> {
        return eventDao.getEventsSince(deviceId, sinceSeq)
    }

    suspend fun getLatestSequenceNumber(deviceId: String): Long {
        return eventDao.getMaxSequenceNumber(deviceId) ?: 0L
    }
}
