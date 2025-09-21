package com.blitztech.pudokiosk.data.db

import androidx.room.*

@Dao
interface AuditDao {
    @Insert suspend fun insert(e: AuditLogEntity)
    @Query("SELECT * FROM audit_log ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<AuditLogEntity>
}
