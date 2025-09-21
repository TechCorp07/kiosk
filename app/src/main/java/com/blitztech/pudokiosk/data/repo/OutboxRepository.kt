package com.blitztech.pudokiosk.data.repo

import com.blitztech.pudokiosk.data.db.OutboxDao
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.blitztech.pudokiosk.data.net.ApiService
import com.blitztech.pudokiosk.data.net.EventsBulkRequest
import com.blitztech.pudokiosk.data.net.OutboxEventDTO

class OutboxRepository(
    private val dao: OutboxDao,
    private val api: ApiService
) {
    suspend fun enqueue(e: OutboxEventEntity) = dao.enqueue(e)

    suspend fun flushOnce(): Boolean {
        val pending = dao.pending()
        if (pending.isEmpty()) return true
        val dto = pending.map {
            OutboxEventDTO(it.idempotencyKey, it.type, it.payloadJson, it.createdAt)
        }
        val resp = api.postEvents(EventsBulkRequest(dto))
        if (resp.delivered.isNotEmpty()) dao.markDelivered(resp.delivered)
        // return true if all delivered; false triggers retry
        return resp.delivered.size == pending.size
    }
}
