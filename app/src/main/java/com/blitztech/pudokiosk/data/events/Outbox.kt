package com.blitztech.pudokiosk.data.events

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.blitztech.pudokiosk.data.ServiceLocator
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.blitztech.pudokiosk.util.Ids

object Outbox {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun enqueue(type: EventType, payload: Map<String, Any?>) {
        val adapter = moshi.adapter(Map::class.java)
        val json = adapter.toJson(payload)
        val row = OutboxEventEntity(
            idempotencyKey = Ids.uuid(),
            type = type.value,
            payloadJson = json,
            createdAt = Ids.now()
        )
        ServiceLocator.outbox.enqueue(row)
    }
}
