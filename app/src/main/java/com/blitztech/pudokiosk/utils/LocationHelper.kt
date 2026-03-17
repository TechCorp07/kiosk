package com.blitztech.pudokiosk.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper class for location services
 */
object LocationHelper {
    private const val TAG = "LocationHelper"

    // Default location: Harare, Zimbabwe
    private const val DEFAULT_LATITUDE = -17.8252
    private const val DEFAULT_LONGITUDE = 31.0335

    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get current location or default if unavailable
     */
    fun getCurrentLocation(context: Context): Pair<Double, Double> {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted, using default location")
            return Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try GPS first
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                Pair(location.latitude, location.longitude)
            } else {
                Log.w(TAG, "Location not available, using default")
                Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }
    }

    /**
     * Check if location services are enabled
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Get location accuracy description
     */
    fun getLocationAccuracy(location: Location): String {
        return if (location.hasAccuracy()) {
            val accuracy = location.accuracy
            when {
                accuracy < 10 -> "High (${accuracy.toInt()}m)"
                accuracy < 50 -> "Medium (${accuracy.toInt()}m)"
                else -> "Low (${accuracy.toInt()}m)"
            }
        } else {
            "Unknown"
        }
    }

    /**
     * Format coordinates for display
     */
    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return String.format("%.6f, %.6f", latitude, longitude)
    }

    /**
     * Calculate distance between two points (Haversine formula)
     * Returns distance in kilometers
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371.0 // Earth radius in kilometers

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }
}