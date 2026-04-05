package com.blitztech.pudokiosk.data.api

import android.content.Context
import com.blitztech.pudokiosk.auth.AuthInterceptor
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.technician.DeveloperModeActivity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    /** Set from ZimpudoApp.onCreate(). Controls HTTP logging verbosity. */
    var isDebug: Boolean = false

    fun provideOkHttpClient(prefs: Prefs): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (isDebug)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(prefs))  // Auth before logging
            .addInterceptor(loggingInterceptor)
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi, prefs: Prefs): Retrofit {
        val baseUrl = when (prefs.getString(DeveloperModeActivity.KEY_ENVIRONMENT, DeveloperModeActivity.ENV_PRODUCTION)) {
            DeveloperModeActivity.ENV_STAGING -> "https://staging.zimpudo.com:8222/"
            DeveloperModeActivity.ENV_LOCAL -> "http://10.0.2.2:8222/" // Emulator localhost
            DeveloperModeActivity.ENV_CUSTOM -> prefs.getString(DeveloperModeActivity.KEY_CUSTOM_URL, ApiConfig.BASE_URL).ifBlank { ApiConfig.BASE_URL }
            else -> ApiConfig.BASE_URL
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    fun provideApiRepository(apiService: ApiService, context: Context): ApiRepository {
        return ApiRepository(apiService, context)
    }
}