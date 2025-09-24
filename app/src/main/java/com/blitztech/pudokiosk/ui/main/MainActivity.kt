package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.blitztech.pudokiosk.databinding.ActivityMainBinding
import com.blitztech.pudokiosk.ui.TechnicianAccessActivity
import com.blitztech.pudokiosk.ui.onboarding.LanguageSelectionActivity

/**
 * Main entry point for ZIMPUDO Kiosk
 * Shows splash screen then navigates to language selection
 * Has hidden technician access via gesture
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetectorCompat

    private var tapCount = 0
    private var lastTapTime = 0L
    private val techAccessTapCount = 7  // 7 rapid taps for tech access
    private val tapTimeoutMs = 1000L    // Reset tap count after 1 second

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "MainActivity.onCreate() called")

        try {
            super.onCreate(savedInstanceState)

            Log.d(TAG, "Inflating layout...")
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "Layout inflated successfully")

            Log.d(TAG, "Setting up technician access...")
            setupTechnicianAccess()

            Log.d(TAG, "Setting up auto-navigation timer (3 seconds)...")
            // Auto-navigate to language selection after 3 seconds
            binding.root.postDelayed({
                Log.d(TAG, "Auto-navigation timer triggered")
                navigateToLanguageSelection()
            }, 3000)

            // Allow manual tap to skip splash
            binding.root.setOnClickListener {
                Log.d(TAG, "Manual tap detected - skipping splash")
                navigateToLanguageSelection()
            }

            Log.d(TAG, "MainActivity.onCreate() completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error in MainActivity.onCreate()", e)
            // Try to recover gracefully
            tryRecovery()
        }
    }

    private fun tryRecovery() {
        Log.w(TAG, "Attempting recovery...")
        try {
            // Simple fallback - just navigate after a delay
            Thread.sleep(1000) // Give a moment for things to settle
            navigateToLanguageSelection()
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed", e)
            // Last resort - finish activity
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity.onResume() called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity.onPause() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity.onDestroy() called")
    }

    private fun setupTechnicianAccess() {
        try {
            gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val currentTime = System.currentTimeMillis()

                    // Reset tap count if too much time passed
                    if (currentTime - lastTapTime > tapTimeoutMs) {
                        tapCount = 0
                    }

                    tapCount++
                    lastTapTime = currentTime

                    Log.d(TAG, "Tap count: $tapCount (need $techAccessTapCount for tech access)")

                    // Check if we reached the tech access tap count
                    if (tapCount >= techAccessTapCount) {
                        Log.d(TAG, "Technician access sequence detected!")
                        navigateToTechnicianAccess()
                        return true
                    }

                    return false
                }
            })
            Log.d(TAG, "Technician access gesture detector setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup technician access", e)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return try {
            gestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onTouchEvent", e)
            super.onTouchEvent(event)
        }
    }

    private fun navigateToLanguageSelection() {
        try {
            Log.d(TAG, "Navigating to LanguageSelectionActivity...")
            val intent = Intent(this, LanguageSelectionActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to LanguageSelectionActivity", e)
            // Try alternative navigation or show error
            tryAlternativeNavigation()
        }
    }

    private fun navigateToTechnicianAccess() {
        try {
            Log.d(TAG, "Navigating to TechnicianAccessActivity...")
            val intent = Intent(this, TechnicianAccessActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to TechnicianAccessActivity", e)
        }
    }

    private fun tryAlternativeNavigation() {
        Log.w(TAG, "Attempting alternative navigation...")
        // Could implement alternative flows or error screens here
        // For now, just log the issue
        Log.e(TAG, "All navigation attempts failed")
    }
}