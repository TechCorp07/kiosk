package com.blitztech.pudokiosk.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityMainBinding
import com.blitztech.pudokiosk.service.KioskLockManager
import com.blitztech.pudokiosk.service.KioskModeService
import com.blitztech.pudokiosk.ui.technician.KioskProvisioningActivity
import com.blitztech.pudokiosk.ui.technician.TechnicianAccessActivity
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.onboarding.LanguageSelectionActivity


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SPLASH_DELAY_MS = 3000L
        private const val REQ_CODE_PERMISSIONS = 1001

        /**
         * All dangerous permissions the kiosk needs.
         * Requested once during first boot / provisioning.
         */
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            // Storage permissions only needed on pre-Q (API < 29)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
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

        // Apply display scaling BEFORE super/setContentView (same logic as BaseKioskActivity)
        applyDisplayScaling()

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
            KioskLockManager.ensureLockTaskOnStartup(this)

            // Request all dangerous permissions upfront (first boot only)
            ensurePermissions()

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

        // Always re-enable kiosk mode on resume (unless maintenance mode is active)
        if (kioskModeEnabled && !KioskLockManager.isMaintenanceMode()) {
            hideSystemUI()
            startUIVisibilityMonitoring()
            KioskLockManager.enableLockTaskMode(this)
        }

        // Auto-return timer for accidental navigation
        if (!splashNavigationPending) {
            Log.d(TAG, "MainActivity resumed - starting return timer (5 seconds)")
            handler.postDelayed({
                Log.d(TAG, "Auto-return timer triggered")
                navigateToNextScreen()
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
                navigateToNextScreen()
            }
        }

        handler.postDelayed({
            if (splashNavigationPending) {
                Log.d(TAG, "Auto-navigation timer triggered")
                navigateToNextScreen()
            }
        }, SPLASH_DELAY_MS)
    }

    private fun cancelScheduledNavigation() {
        splashNavigationPending = false
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Gate: if not provisioned → provisioning wizard, else → customer UI.
     */
    private fun navigateToNextScreen() {
        if (!ZimpudoApp.prefs.isProvisioned()) {
            Log.d(TAG, "Kiosk NOT provisioned — routing to provisioning wizard")
            navigateToProvisioning()
        } else {
            navigateToLanguageSelection()
        }
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

    private fun navigateToProvisioning() {
        try {
            splashNavigationPending = false
            Log.d(TAG, "Navigating to KioskProvisioningActivity...")

            val intent = Intent(this, KioskProvisioningActivity::class.java)
            startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to KioskProvisioningActivity", e)
            handler.postDelayed({ navigateToProvisioning() }, 1000)
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

    // ── Permission Handling ─────────────────────────────────────

    /**
     * Request all dangerous permissions upfront on first boot.
     * On a kiosk, the technician grants these once during setup;
     * they should NEVER pop up during customer/courier flows.
     */
    private fun ensurePermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Log.d(TAG, "Requesting ${missing.size} missing permissions: $missing")
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_CODE_PERMISSIONS)
        } else {
            Log.d(TAG, "All required permissions already granted")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CODE_PERMISSIONS) {
            val denied = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }
            if (denied.isEmpty()) {
                Log.d(TAG, "All permissions granted ✅")
            } else {
                Log.w(TAG, "Some permissions denied (can be granted later in provisioning): $denied")
            }
        }
    }

    // ── Display Scaling ─────────────────────────────────────────

    /**
     * Applies the same density scaling as [BaseKioskActivity] so the splash screen
     * matches the scaling applied to all other activities.
     */
    private fun applyDisplayScaling() {
        try {
            val prefs = ZimpudoApp.prefs

            val manualScale = prefs.getFloat(BaseKioskActivity.KEY_DISPLAY_SCALE_OVERRIDE, 0f)
            val metrics = resources.displayMetrics

            val scaleFactor: Float = if (manualScale > 0f) {
                manualScale.coerceIn(0.75f, 1.0f)
            } else {
                // Auto-detect from actual screen measurements
                val widthInches = metrics.widthPixels.toDouble() / metrics.xdpi
                val heightInches = metrics.heightPixels.toDouble() / metrics.ydpi
                val actualDiagonal = Math.sqrt(widthInches * widthInches + heightInches * heightInches)
                (actualDiagonal / 10.1).toFloat().coerceIn(0.75f, 1.0f)
            }

            if (scaleFactor < 1.0f) {
                val sysMetrics = android.content.res.Resources.getSystem().displayMetrics
                val originalDensity = sysMetrics.density
                val fontScale = sysMetrics.scaledDensity / originalDensity

                val targetDensity = originalDensity * scaleFactor
                val targetScaledDensity = targetDensity * fontScale
                val targetDensityDpi = (targetDensity * 160).toInt()

                applicationContext.resources.displayMetrics.apply {
                    density = targetDensity
                    scaledDensity = targetScaledDensity
                    densityDpi = targetDensityDpi
                }
                metrics.density = targetDensity
                metrics.scaledDensity = targetScaledDensity
                metrics.densityDpi = targetDensityDpi

                Log.d(TAG, "Display scaling applied: factor=${"%.2f".format(scaleFactor)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Display scaling failed — using system defaults", e)
        }
    }
}