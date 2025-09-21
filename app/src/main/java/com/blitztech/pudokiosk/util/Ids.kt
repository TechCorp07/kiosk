package com.blitztech.pudokiosk.util

import java.util.UUID

object Ids {
    fun uuid(): String = UUID.randomUUID().toString()
    fun now(): Long = System.currentTimeMillis()
}
