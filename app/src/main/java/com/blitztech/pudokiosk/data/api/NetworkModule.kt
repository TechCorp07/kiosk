package com.blitztech.pudokiosk.data.api

import android.content.Context
import com.blitztech.pudokiosk.auth.AuthInterceptor
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.prefs.Prefs
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    fun provideOkHttpClient(prefs: Prefs): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
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
        // Production: always use the configured base URL
        // API URL override is supported for staging kiosks via provisioning screen
        val overrideUrl = prefs.getApiBaseUrlOverride()
        val baseUrl = if (overrideUrl.isNotBlank()) overrideUrl else ApiConfig.BASE_URL

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