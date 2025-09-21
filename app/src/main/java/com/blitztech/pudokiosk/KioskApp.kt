package com.blitztech.pudokiosk

import android.app.Application
import com.blitztech.pudokiosk.data.ServiceLocator

class KioskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
