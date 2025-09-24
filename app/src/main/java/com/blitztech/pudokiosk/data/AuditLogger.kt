package com.blitztech.pudokiosk.data

import com.blitztech.pudokiosk.data.db.AuditLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

object AuditLogger {
    private val scope = CoroutineScope(Dispatchers.IO)
    fun log(level: String, event: String, details: String? = null) {
        val row = AuditLogEntity(
            ts = System.currentTimeMillis(),
            level = level,
            event = event,
            details = details
        )
    }
}
