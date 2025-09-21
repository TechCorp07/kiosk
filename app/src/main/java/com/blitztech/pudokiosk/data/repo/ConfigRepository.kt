package com.blitztech.pudokiosk.data.repo

import com.blitztech.pudokiosk.data.ServiceLocator
import com.blitztech.pudokiosk.data.db.ConfigDao
import com.blitztech.pudokiosk.data.db.ConfigEntity
import com.squareup.moshi.Moshi

class ConfigRepository(private val dao: ConfigDao) {
    suspend fun local(): ConfigEntity? = dao.get()
    suspend fun save(version: Long, json: String) =
        dao.upsert(ConfigEntity(version = version, json = json, updatedAt = System.currentTimeMillis()))

    suspend fun refresh(deviceId: String): Boolean {
        val remote = ServiceLocator.api.fetchConfig(deviceId)
        val cur = dao.get()
        if (cur == null || remote.version > cur.version) {
            save(remote.version, remote.json)
            return true
        }
        return false
    }
}
