package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.blitztech.pudokiosk.databinding.ActivityMainBinding
import com.blitztech.pudokiosk.service.KioskModeService
import com.blitztech.pudokiosk.ui.technician.TechnicianAccessActivity
import com.blitztech.pudokiosk.ui.onboarding.LanguageSelectionActivity


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY_MS = 3000L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetectorCompat
    private val handler = Handler(Looper.getMainLooper())
    private val uiHideHandler = Handler(Looper.getMainLooper())

    // Technician access variables
    private var tapCount = 0
    private var lastTapTime = 0L
    private val techAccessTapCount = 7
    private val tapTimeoutMs = 2000L
    private var splashNavigationPending = false

    // Kiosk mode variables
    private var kioskModeEnabled = true
    private val UI_HIDE_DELAY = 3000L // Hide UI after 3 seconds of inactivity

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "MainActivity.onCreate() called")
        super.onCreate(savedInstanceState)

        try {
            // MUST setup kiosk mode BEFORE setting content view
            setupTrueKioskMode()

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "Layout inflated successfully")

            setupTechnicianAccess()
            setupBackButtonHandling()
            setupSystemUIHiding()

            KioskModeService.start(this)
            scheduleNavigation()

            // Start monitoring for system UI visibility changes
            startUIVisibilityMonitoring()

            Log.d(TAG, "TRUE KIOSK MODE activated")

        } catch (e: Exception) {
            Log.e(TAG, "Error in MainActivity.onCreate()", e)
            navigateToLanguageSelection()
        }
    }

    private fun setupTrueKioskMode() {
        // Make activity full screen and hide all system UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+ - Handle notch/cutout areas
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.apply {
            // Request fullscreen window
            requestFeature(Window.FEATURE_NO_TITLE)

            // Add all necessary flags for true kiosk mode
            addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            // Prevent screenshots/screen recording for security
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Hide system UI completely
        hideSystemUI()
    }

    private fun hideSystemUI() {
        // This method hides ALL system UI elements
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19+ Immersive mode
            decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        } else {
            // API 25 fallback
            decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }

    private fun setupSystemUIHiding() {
        // Listen for system UI visibility changes and re-hide immediately
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            Log.d(TAG, "System UI visibility changed: $visibility")

            // If system UI becomes visible, hide it again immediately
            if (kioskModeEnabled && (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                Log.d(TAG, "System UI appeared - hiding again")
                hideSystemUI()
            }
        }

        // Monitor for any touches that might reveal system UI
        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Reset the hide timer on any touch
                    resetUIHideTimer()
                    gestureDetector.onTouchEvent(event)
                }
                MotionEvent.ACTION_UP -> {
                    // Ensure UI stays hidden after touch
                    scheduleUIHide()
                    gestureDetector.onTouchEvent(event)
                }
                else -> gestureDetector.onTouchEvent(event)
            }
            false // Allow other touch events to proceed
        }
    }

    private fun startUIVisibilityMonitoring() {
        // Continuously monitor and enforce UI hiding
        val monitoringRunnable = object : Runnable {
            override fun run() {
                if (kioskModeEnabled) {
                    hideSystemUI()
                    // Check again in 1 second
                    uiHideHandler.postDelayed(this, 1000)
                }
            }
        }
        uiHideHandler.post(monitoringRunnable)
    }

    private fun resetUIHideTimer() {
        uiHideHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleUIHide() {
        resetUIHideTimer()
        uiHideHandler.postDelayed({ hideSystemUI() }, UI_HIDE_DELAY)
    }

    private fun setupBackButtonHandling() {
        // Completely disable back button for kiosk mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button pressed - BLOCKED in kiosk mode")
                // Show a subtle indication that back button was pressed
                showKioskModeMessage()
            }
        })
    }

    private fun showKioskModeMessage() {
        // Optional: Brief message to indicate kiosk mode is active
        // You can remove this if you don't want any indication
        Log.d(TAG, "Kiosk mode active - system navigation disabled")
    }

    private fun setupTechnicianAccess() {
        try {
            gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    return handleTechnicianAccessTap()
                }
            })
            Log.d(TAG, "Technician access gesture detector setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup technician access", e)
        }
    }

    private fun handleTechnicianAccessTap(): Boolean {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastTapTime > tapTimeoutMs) {
            tapCount = 0
        }

        tapCount++
        lastTapTime = currentTime

        Log.d(TAG, "Tap count: $tapCount (need $techAccessTapCount for tech access)")

        if (tapCount >= techAccessTapCount) {
            Log.d(TAG, "Technician access sequence detected!")
            // Temporarily disable kiosk mode for technician access
            disableKioskMode()
            navigateToTechnicianAccess()
            return true
        }

        return false
    }

    private fun disableKioskMode() {
        kioskModeEnabled = false
        uiHideHandler.removeCallbacksAndMessages(null)

        // Restore normal system UI for technician
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        Log.d(TAG, "Kiosk mode temporarily disabled for technician access")
    }

    private fun enableKioskMode() {
        kioskModeEnabled = true
        hideSystemUI()
        startUIVisibilityMonitoring()

        Log.d(TAG, "Kiosk mode re-enabled")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity.onResume() called")

        // Always re-enable kiosk mode on resume (unless technician is active)
        if (kioskModeEnabled) {
            hideSystemUI()
            startUIVisibilityMonitoring()
        }

        // Auto-return timer for accidental navigation
        if (!splashNavigationPending) {
            Log.d(TAG, "MainActivity resumed - starting return timer (5 seconds)")
            handler.postDelayed({
                Log.d(TAG, "Auto-return timer triggered - going to language selection")
                navigateToLanguageSelection()
            }, 5000L)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity.onPause() called")
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity.onDestroy() called")
        handler.removeCallbacksAndMessages(null)
        uiHideHandler.removeCallbacksAndMessages(null)
    }

    // Override to prevent activity from finishing
    override fun finish() {
        if (kioskModeEnabled) {
            Log.d(TAG, "finish() called but blocked in kiosk mode")
            // Don't call super.finish() - prevent app from closing
        } else {
            super.finish()
        }
    }

    // Override to prevent moving to background
    override fun moveTaskToBack(nonRoot: Boolean): Boolean {
        if (kioskModeEnabled) {
            Log.d(TAG, "moveTaskToBack() called but blocked in kiosk mode")
            return false // Prevent moving to background
        }
        return super.moveTaskToBack(nonRoot)
    }

    // Prevent window focus loss in kiosk mode
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (kioskModeEnabled && !hasFocus) {
            Log.d(TAG, "Window focus lost - attempting to regain focus")

            // Try to bring app back to foreground
            handler.postDelayed({
                hideSystemUI()
            }, 100)
        }
    }

    // Handle home button press (API 25 compatible)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (kioskModeEnabled) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
    }

    private fun scheduleNavigation() {
        splashNavigationPending = true

        binding.root.setOnClickListener {
            Log.d(TAG, "Manual tap detected")
            if (splashNavigationPending) {
                Log.d(TAG, "Skipping splash screen")
                cancelScheduledNavigation()
                navigateToLanguageSelection()
            }
        }

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

    private fun navigateToLanguageSelection() {
        try {
            splashNavigationPending = false
            Log.d(TAG, "Navigating to LanguageSelectionActivity...")

            val intent = Intent(this, LanguageSelectionActivity::class.java)
            startActivity(intent)
            // Don't call finish() to maintain kiosk mode

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to LanguageSelectionActivity", e)
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
            // Re-enable kiosk mode if navigation fails
            enableKioskMode()
        }
    }
}