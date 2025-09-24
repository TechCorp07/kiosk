package com.blitztech.pudokiosk

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.prefs.Prefs
import java.util.*

class ZimpudoApp : Application() {

    companion object {
        private const val TAG = "ZimpudoApp"

        // Global instance for easy access
        lateinit var instance: ZimpudoApp
            private set

        // Application-wide singletons
        val prefs: Prefs by lazy { Prefs(instance) }
        val apiRepository: ApiRepository by lazy {
            val okHttpClient = NetworkModule.provideOkHttpClient()
            val moshi = NetworkModule.provideMoshi()
            val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
            val apiService = NetworkModule.provideApiService(retrofit)
            NetworkModule.provideApiRepository(apiService, instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "ZIMPUDO Kiosk Application starting...")

        try {
            initializeGlobalSettings()
            initializeErrorHandling()

            Log.d(TAG, "Application initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during application initialization", e)
        }
    }

    private fun initializeGlobalSettings() {
        // Load saved locale and apply globally
        val savedLocale = prefs.getLocale()
        applyLocale(savedLocale)

        Log.d(TAG, "Global settings initialized - Locale: $savedLocale")
    }

    private fun applyLocale(localeCode: String) {
        val locale = when (localeCode) {
            "sn" -> Locale("sn", "ZW") // Shona
            "nd" -> Locale("nd", "ZW") // Ndebele
            else -> Locale("en", "ZW") // English default
        }

        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun initializeErrorHandling() {
        // Set up global uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", exception)

            // Log critical error for diagnosis
            logCriticalError(exception)

            // Call the default handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, exception)
        }

        Log.d(TAG, "Global error handling initialized")
    }

    private fun logCriticalError(exception: Throwable) {
        try {
            val errorDetails = """
                === ZIMPUDO KIOSK CRITICAL ERROR ===
                Time: ${System.currentTimeMillis()}
                Thread: ${Thread.currentThread().name}
                Error: ${exception.message}
                Stack: ${exception.stackTrace.take(10).joinToString("\n")}
                
                Device Info: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                App Version: 0.1.0
            """.trimIndent()

            Log.e(TAG, errorDetails)

            // Could also write to file or send to crash reporting service

        } catch (e: Exception) {
            Log.e(TAG, "Failed to log critical error", e)
        }
    }

    override fun onTerminate() {
        Log.d(TAG, "ZIMPUDO Kiosk Application terminating...")
        super.onTerminate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning - attempting cleanup")
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "Memory trim requested - Level: $level")

        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "Critical memory situation - performing cleanup")
            }
            TRIM_MEMORY_BACKGROUND -> {
                Log.d(TAG, "App moved to background - light cleanup")
            }
        }
    }
}

// Extension function for easy access from activities
val Context.app: ZimpudoApp
    get() = applicationContext as ZimpudoApp