package com.blitztech.pudokiosk.data

import android.content.Context
import androidx.room.Room
import com.blitztech.pudokiosk.data.api.ApiService
import com.blitztech.pudokiosk.data.db.AppDatabase
import com.blitztech.pudokiosk.data.repository.OutboxRepository
import com.blitztech.pudokiosk.secure.SecretStore
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ServiceLocator {
    lateinit var db: AppDatabase; private set
    lateinit var api: ApiService; private set
    lateinit var outbox: OutboxRepository; private set
    lateinit var config: com.blitztech.pudokiosk.data.repository.ConfigRepository; private set

    fun init(ctx: Context) {
        // 1) Encrypted Room
        val passphrase = SecretStore(ctx).dbPassphrase()
        val factory = net.sqlcipher.database.SupportFactory(passphrase)

        fun buildDb(name: String) = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()  // dev-friendly; add real migrations later
            .build()

        val dbName = "pudo.db"   // keep your existing name

        // Try open; if it's an old plaintext DB, wipe and recreate encrypted.
        db = try {
            buildDb(dbName).also { it.openHelper.writableDatabase } // force open now
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("file is not a database", ignoreCase = true)) {
                ctx.deleteDatabase(dbName)
                buildDb(dbName).also { it.openHelper.writableDatabase }
            } else {
                throw e
            }
        }

        config = com.blitztech.pudokiosk.data.repository.ConfigRepository(db.config())

        // 2) OkHttp + TLS pinning (replace host + pin with your values)
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val pinner = CertificatePinner.Builder()
            // .add("api.yourdomain.com", "sha256/REPLACE_WITH_YOUR_PIN_BASE64==")
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .certificatePinner(pinner)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/") // TODO your backend base URL
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
            .build()

        api = retrofit.create(ApiService::class.java)
        outbox = OutboxRepository(db.outbox(), api)
    }
}
