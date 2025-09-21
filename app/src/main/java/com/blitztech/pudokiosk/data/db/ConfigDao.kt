package com.blitztech.pudokiosk.data.db

import androidx.room.*

@Dao
interface ConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: ConfigEntity)
    @Query("SELECT * FROM config WHERE `key` = 'device' LIMIT 1") suspend fun get(): ConfigEntity?
}
