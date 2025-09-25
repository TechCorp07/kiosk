package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.blitztech.pudokiosk.databinding.ActivityMainBinding
import com.blitztech.pudokiosk.service.KioskModeService
import com.blitztech.pudokiosk.ui.Technician.TechnicianAccessActivity
import com.blitztech.pudokiosk.ui.onboarding.LanguageSelectionActivity

/**
 * Main entry point for ZIMPUDO Kiosk
 * Shows splash screen then navigates to language selection
 * Has hidden technician access via 7 rapid taps on logo area
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY_MS = 3000L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetectorCompat
    private val handler = Handler(Looper.getMainLooper())

    // Technician access variables
    private var tapCount = 0
    private var lastTapTime = 0L
    private val techAccessTapCount = 7  // 7 rapid taps for tech access
    private val tapTimeoutMs = 2000L    // Reset tap count after 2 seconds (increased from 1)
    private var splashNavigationPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "MainActivity.onCreate() called")

        super.onCreate(savedInstanceState)

        try {
            // Setup kiosk mode UI
            setupKioskMode()

            // Inflate layout
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "Layout inflated successfully")

            // Setup technician access gesture detection
            setupTechnicianAccess()

            // Handle back button for kiosk mode
            setupBackButtonHandling()

            // Start kiosk service
            KioskModeService.start(this)

            // Auto-navigate to language selection after splash delay
            scheduleNavigation()

            Log.d(TAG, "MainActivity.onCreate() completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error in MainActivity.onCreate()", e)
            // Try to navigate directly in case of error
            navigateToLanguageSelection()
        }
    }

    private fun setupKioskMode() {
        // Keep screen on and disable keyguard
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // Disable status bar and navigation bar for true kiosk experience
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun setupBackButtonHandling() {
        // Override back button behavior for kiosk mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button pressed - ignoring (kiosk mode)")
                // Do nothing - prevent back button from closing app
                // Only technician access can exit the app flow
            }
        })
    }

    private fun setupTechnicianAccess() {
        try {
            gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    return handleTechnicianAccessTap()
                }
            })

            // Make the entire root view listen for taps
            binding.root.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false // Allow other touch events to proceed
            }

            Log.d(TAG, "Technician access gesture detector setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup technician access", e)
        }
    }

    private fun handleTechnicianAccessTap(): Boolean {
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

    private fun scheduleNavigation() {
        splashNavigationPending = true

        // Allow manual tap to skip splash (but still count towards tech access)
        binding.root.setOnClickListener {
            Log.d(TAG, "Manual tap detected")
            if (splashNavigationPending) {
                Log.d(TAG, "Skipping splash screen")
                cancelScheduledNavigation()
                navigateToLanguageSelection()
            }
        }

        // Auto-navigate after delay
        handler.postDelayed({
            if (splashNavigationPending) {
                Log.d(TAG, "Auto-navigation timer triggered")
                navigateToLanguageSelection()
            }
        }, SPLASH_DELAY_MS)
    }

    private fun cancelScheduledNavigation() {
        splashNavigationPending = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun navigateToLanguageSelection() {
        try {
            splashNavigationPending = false
            Log.d(TAG, "Navigating to LanguageSelectionActivity...")

            val intent = Intent(this, LanguageSelectionActivity::class.java)
            startActivity(intent)
            // Don't call finish() to maintain kiosk mode

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to LanguageSelectionActivity", e)
            // Try again after a short delay
            handler.postDelayed({ navigateToLanguageSelection() }, 1000)
        }
    }

    private fun navigateToTechnicianAccess() {
        try {
            cancelScheduledNavigation()
            Log.d(TAG, "Navigating to TechnicianAccessActivity...")

            val intent = Intent(this, TechnicianAccessActivity::class.java)
            startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to TechnicianAccessActivity", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity.onResume() called")

        // Re-apply kiosk mode settings
        setupKioskMode()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity.onPause() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity.onDestroy() called")
        handler.removeCallbacksAndMessages(null)
    }
}