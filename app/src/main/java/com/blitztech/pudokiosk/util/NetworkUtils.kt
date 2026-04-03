package com.blitztech.pudokiosk.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Network connectivity utilities.
 * API 25 (Android 7.1.2) compatible.
 */
object NetworkUtils {

    /**
     * Returns true if the device has active network connectivity.
     * Uses NetworkCapabilities (API 23+) when available, falls back to
     * the deprecated activeNetworkInfo for API < 23.
     */
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // API 23+ path — safe on our API 25 target
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }
}
