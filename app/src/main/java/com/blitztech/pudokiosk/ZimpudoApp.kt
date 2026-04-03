package com.blitztech.pudokiosk

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.db.AppDatabase
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.deviceio.HardwareWatchdog
import com.blitztech.pudokiosk.offline.OfflineCollectionManager
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.sync.SyncScheduler
import com.blitztech.pudokiosk.update.UpdateCheckWorker
import com.blitztech.pudokiosk.util.NetworkUtils
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import java.util.*

class ZimpudoApp : Application() {

    companion object {
        private const val TAG = "ZimpudoApp"

        // Global instance for easy access
        lateinit var instance: ZimpudoApp
            private set

        // Application-wide singletons - Initialize safely
        val prefs: Prefs by lazy {
            try {
                Log.d(TAG, "Initializing Prefs...")
                Prefs(instance)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Prefs", e)
                throw e
            }
        }

        val apiRepository: ApiRepository by lazy {
            try {
                Log.d(TAG, "Initializing API Repository...")
                val okHttpClient = NetworkModule.provideOkHttpClient(prefs)
                val moshi = NetworkModule.provideMoshi()
                val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
                val apiService = NetworkModule.provideApiService(retrofit)
                NetworkModule.provideApiRepository(apiService, instance)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize API Repository", e)
                throw e
            }
        }

        /** Shared Room database instance — encrypted with SQLCipher. */
        val database: AppDatabase by lazy {
            try {
                Log.d(TAG, "Initializing encrypted AppDatabase (SQLCipher)...")
                val passphrase = getOrCreateDbPassphrase()
                val factory = SupportFactory(passphrase)
                Room.databaseBuilder(
                    instance,
                    AppDatabase::class.java,
                    "pudokiosk_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(true)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AppDatabase", e)
                throw e
            }
        }

        /**
         * Returns the stored DB passphrase, or generates + stores a new one on first boot.
         *
         * The passphrase is 32 bytes of SecureRandom entropy, Base64-encoded and stored
         * in EncryptedSharedPreferences (AES-256-GCM — already used by [Prefs]).
         * This means the passphrase is encrypted at rest using the Android Keystore.
         *
         * Note: `prefs` must be initialized before `database` is accessed.
         */
        private fun getOrCreateDbPassphrase(): ByteArray {
            val KEY = "db_passphrase_v1"
            var encoded = prefs.getString(KEY, "")
            if (encoded.isBlank()) {
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
                prefs.putString(KEY, encoded)
                Log.d(TAG, "Generated new DB passphrase (first boot)")
            } else {
                Log.d(TAG, "Using existing DB passphrase")
            }
            return Base64.decode(encoded, Base64.NO_WRAP)
        }

        /** Network connectivity utilities (API 25 compatible). */
        val networkUtils: NetworkUtils get() = NetworkUtils

        /** Offline OTP validation manager for recipient collection. */
        val offlineCollectionManager: OfflineCollectionManager by lazy {
            OfflineCollectionManager(database)
        }

        /** RS485 hardware watchdog — pings locker board every 30s, reconnects on failure. */
        val hardwareWatchdog: HardwareWatchdog by lazy {
            HardwareWatchdog(instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "ZIMPUDO Kiosk Application starting...")
        Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.d(TAG, "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")

        try {
            // Configure network logging level before API client init
            NetworkModule.isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

            // Initialize in safe order with timeouts
            initializeErrorHandling()
            initializeGlobalSettings()

            Log.d(TAG, "Application initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during application initialization", e)
            // Don't rethrow - allow app to continue with defaults
            initializeDefaults()
        }
    }

    private fun initializeGlobalSettings() {
        try {
            Log.d(TAG, "Loading global settings...")

            val savedLocale = try {
                prefs.getLocale()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load saved locale, using default", e)
                "en"
            }

            applyLocale(savedLocale)

            // Start background event sync (outbox drain)
            SyncScheduler.schedule(this)

            // Start locker cell sync + heartbeat every 15 minutes
            SyncScheduler.scheduleLockerSync(this)

            // Immediate locker sync on startup to prime cell inventory
            SyncScheduler.enqueueLockerSyncNow(this)

            // Schedule periodic OTA update checks
            UpdateCheckWorker.schedule(this)

            // Start hardware watchdog — RS485 health ping every 30s
            try {
                hardwareWatchdog.start()
            } catch (e: Exception) {
                Log.w(TAG, "HardwareWatchdog failed to start: ${e.message}")
            }

            Log.d(TAG, "Global settings initialized - Locale: $savedLocale")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize global settings", e)
            applyLocale("en")
        }
    }

    private fun initializeDefaults() {
        Log.w(TAG, "Initializing with default settings due to initialization failure")
        try {
            applyLocale("en")
            Log.d(TAG, "Default settings applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Even default initialization failed", e)
        }
    }

    private fun applyLocale(localeCode: String) {
        try {
            val locale = when (localeCode) {
                "sn" -> Locale("sn", "ZW") // Shona
                "nd" -> Locale("nd", "ZW") // Ndebele
                else -> Locale("en", "ZW") // English default
            }

            Locale.setDefault(locale)

            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)

            Log.d(TAG, "Locale applied successfully: $localeCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply locale: $localeCode", e)
        }
    }

    private fun initializeErrorHandling() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize error handling", e)
        }
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