package com.blitztech.pudokiosk.ui.base

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Enhanced Base Activity for maintaining TRUE KIOSK MODE across all app activities.
 * All activities in the kiosk flow should extend this class.
 *
 * Includes automatic display density scaling so the UI designed for a reference
 * 10.1" (1280×800) emulator fits correctly on physical kiosks whose usable
 * display area is smaller than the nominal panel size.
 */
abstract class BaseKioskActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BaseKioskActivity"
        private const val UI_HIDE_DELAY = 3000L
        /** Inactivity timeout before auto‑reset to home screen (5 minutes). */
        private const val INACTIVITY_TIMEOUT_MS = 300_000L

        /**
         * Reference diagonal in inches — the emulator / design target.
         * All layouts & dimens are tuned for this size.
         */
        private const val REFERENCE_DIAGONAL_INCHES = 10.1

        /**
         * Minimum scale we'll ever apply so the UI doesn't become unreadable.
         * 0.75 = allow shrinking to 75% of design size at most.
         */
        private const val MIN_SCALE_FACTOR = 0.75f

        /**
         * Maximum scale — prevents accidental enlargement on bigger panels.
         * 1.0 = never make the UI bigger than the reference design.
         */
        private const val MAX_SCALE_FACTOR = 1.0f

        /** SharedPreferences key for an optional manual override (0.0 = auto). */
        const val KEY_DISPLAY_SCALE_OVERRIDE = "display_scale_override"
    }

    private val uiHideHandler = Handler(Looper.getMainLooper())
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private var kioskModeEnabled = true

    /**
     * Set to true before calling finish() to allow a controlled exit
     * from a workflow activity back to its parent screen.
     * Use [finishSafely] instead of calling finish() directly.
     */
    protected var finishAllowed = false

    /** Cached parent class resolved once in onCreate. */
    private var parentActivityClass: Class<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDisplayScaling()           // ← Scale density BEFORE setContentView
        super.onCreate(savedInstanceState)
        setupKioskMode()
        setupBackButtonHandling()
        cacheParentActivity()
    }

    // ---------------------------------------------------------------------------
    // Display density auto-scaling
    // ---------------------------------------------------------------------------

    /**
     * Adjusts the display density so that layouts designed for [REFERENCE_DIAGONAL_INCHES]
     * scale down proportionally on physical kiosks with smaller usable display areas.
     *
     * How it works:
     *   1. Measure the actual screen diagonal in inches using real DPI values
     *   2. Compute scale = actualDiagonal / referenceDiagonal
     *   3. Apply the scale to the system density and scaledDensity (for sp)
     *
     * This means on a kiosk whose true viewable area is 8.5" instead of 10.1", all
     * dp/sp values automatically shrink by ~16% so everything fits without overflow.
     *
     * A manual override via [KEY_DISPLAY_SCALE_OVERRIDE] in SharedPreferences allows
     * field technicians to fine-tune if auto-detection isn't perfect.
     */
    private fun applyDisplayScaling() {
        try {
            val prefs = com.blitztech.pudokiosk.ZimpudoApp.prefs

            // Check for manual override first (0.0 = auto)
            val manualScale = prefs.getFloat(KEY_DISPLAY_SCALE_OVERRIDE, 0f)

            val metrics = resources.displayMetrics

            val scaleFactor: Float = if (manualScale > 0f) {
                // Field tech set a manual override
                manualScale.coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)
            } else {
                // Auto-detect from actual screen measurements
                computeAutoScaleFactor(metrics)
            }

            // Only apply if we need to scale DOWN (avoid enlarging)
            if (scaleFactor < 1.0f) {
                val sysMetrics = android.content.res.Resources.getSystem().displayMetrics
                val originalDensity = sysMetrics.density
                val originalScaledDensity = sysMetrics.scaledDensity
                val fontScale = originalScaledDensity / originalDensity  // preserve user font prefs

                val targetDensity = originalDensity * scaleFactor
                val targetScaledDensity = targetDensity * fontScale
                val targetDensityDpi = (targetDensity * 160).toInt()

                // Apply to application-level metrics (affects all resource inflation)
                val appMetrics = applicationContext.resources.displayMetrics
                appMetrics.density = targetDensity
                appMetrics.scaledDensity = targetScaledDensity
                appMetrics.densityDpi = targetDensityDpi

                // Apply to activity-level metrics
                metrics.density = targetDensity
                metrics.scaledDensity = targetScaledDensity
                metrics.densityDpi = targetDensityDpi

                Log.d(TAG, "Display scaling applied: factor=${"%.2f".format(scaleFactor)} " +
                        "density=${originalDensity}→${targetDensity} " +
                        "dpi=${(originalDensity * 160).toInt()}→${targetDensityDpi}")
            } else {
                Log.d(TAG, "Display scaling: no adjustment needed (factor=${"%.2f".format(scaleFactor)})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Display scaling failed — using system defaults", e)
        }
    }

    /**
     * Computes the scale factor by comparing the actual physical diagonal of the
     * display to the reference diagonal.
     */
    private fun computeAutoScaleFactor(metrics: DisplayMetrics): Float {
        // Actual physical dimensions in inches
        val widthInches = metrics.widthPixels.toDouble() / metrics.xdpi
        val heightInches = metrics.heightPixels.toDouble() / metrics.ydpi
        val actualDiagonal = Math.sqrt(widthInches * widthInches + heightInches * heightInches)

        val scale = (actualDiagonal / REFERENCE_DIAGONAL_INCHES).toFloat()

        Log.d(TAG, "Screen measurement: " +
                "${metrics.widthPixels}x${metrics.heightPixels}px, " +
                "xdpi=${metrics.xdpi}, ydpi=${metrics.ydpi}, " +
                "diagonal=${"%.1f".format(actualDiagonal)}\" " +
                "(reference=${REFERENCE_DIAGONAL_INCHES}\" → scale=${"%.2f".format(scale)})")

        return scale.coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)
    }

    // ---------------------------------------------------------------------------
    // Touch interception — resets inactivity timer on every touch event
    // ---------------------------------------------------------------------------
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        resetInactivityTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun setupKioskMode() {
        // Apply fullscreen kiosk settings
        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )

            // Secure window (prevent screenshots)
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Hide system UI
        hideSystemUI()
        setupSystemUIHiding()
    }

    private fun hideSystemUI() {
        val decorView = window.decorView

        // API 25 compatible system UI hiding
        decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                )

        Log.d(TAG, "System UI hidden for ${this::class.java.simpleName}")
    }

    private fun setupSystemUIHiding() {
        // Listen for system UI visibility changes
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            Log.d(TAG, "System UI visibility changed: $visibility in ${this::class.java.simpleName}")

            // If system UI becomes visible, hide it again
            if (kioskModeEnabled && (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                Log.d(TAG, "System UI appeared - hiding again")
                scheduleUIHide()
            }
        }
    }

    private fun scheduleUIHide() {
        uiHideHandler.removeCallbacksAndMessages(null)
        uiHideHandler.postDelayed({
            if (kioskModeEnabled) {
                hideSystemUI()
            }
        }, UI_HIDE_DELAY)
    }

    private fun setupBackButtonHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    /**
     * Handle back navigation - override this method in subclasses for custom behavior
     */
    private fun cacheParentActivity() {
        parentActivityClass = try {
            val name = packageManager.getActivityInfo(componentName, 0).parentActivityName
            if (name != null) Class.forName(name) else null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving parent activity", e)
            null
        }
    }

    protected open fun handleBackNavigation() {
        val parentClass = parentActivityClass
        if (parentClass != null) {
            val intent = Intent(this, parentClass).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            return
        }
        navigateToMain()
    }

    /**
     * Navigate to main activity (fallback)
     */
    protected fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Log.d(TAG, "Navigating back to MainActivity from ${this::class.java.simpleName}")
    }

    /**
     * Temporarily disable kiosk mode (for technician access)
     */
    protected fun disableKioskMode() {
        kioskModeEnabled = false
        uiHideHandler.removeCallbacksAndMessages(null)

        // Restore normal system UI
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        Log.d(TAG, "Kiosk mode disabled for ${this::class.java.simpleName}")
    }

    /**
     * Re-enable kiosk mode
     */
    protected fun enableKioskMode() {
        kioskModeEnabled = true
        setupKioskMode()

        Log.d(TAG, "Kiosk mode re-enabled for ${this::class.java.simpleName}")
    }

    override fun onResume() {
        super.onResume()
        if (kioskModeEnabled) {
            hideSystemUI()
            scheduleUIHide()
        }
        resetInactivityTimer()
        Log.d(TAG, "${this::class.java.simpleName} resumed in kiosk mode")
    }

    override fun onPause() {
        super.onPause()
        uiHideHandler.removeCallbacksAndMessages(null)
        inactivityHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHideHandler.removeCallbacksAndMessages(null)
        inactivityHandler.removeCallbacksAndMessages(null)
    }

    // ---------------------------------------------------------------------------
    // Inactivity watchdog
    // ---------------------------------------------------------------------------
    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacksAndMessages(null)
        inactivityHandler.postDelayed({
            onInactivityTimeout()
        }, INACTIVITY_TIMEOUT_MS)
    }

    /**
     * Called when the user has not touched the screen for [INACTIVITY_TIMEOUT_MS].
     * Override in subclasses for custom behaviour; default resets to MainActivity.
     */
    protected open fun onInactivityTimeout() {
        Log.d(TAG, "Inactivity timeout — resetting to home screen")
        // Clear session so the next user starts fresh
        try {
            val prefs = com.blitztech.pudokiosk.ZimpudoApp.prefs
            prefs.clearAuthData()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear auth on timeout", e)
        }
        navigateToMain()
    }

    // ---------------------------------------------------------------------------
    // Controlled finish helpers
    // ---------------------------------------------------------------------------

    /**
     * Preferred way to finish a workflow activity while staying in kiosk mode.
     * Sets [finishAllowed] then calls finish() so the kiosk guard lets it through.
     */
    protected fun finishSafely() {
        finishAllowed = true
        super.finish()
    }

    /** Kiosk-safe finish: only allowed when [finishAllowed] is true or kiosk mode is off. */
    override fun finish() {
        if (kioskModeEnabled && !finishAllowed && !isFinishing) {
            Log.d(TAG, "finish() blocked in kiosk mode for ${this::class.java.simpleName}")
            return
        }
        super.finish()
    }

    // Prevent moving task to background
    override fun moveTaskToBack(nonRoot: Boolean): Boolean {
        if (kioskModeEnabled) {
            Log.d(TAG, "moveTaskToBack() blocked in kiosk mode for ${this::class.java.simpleName}")
            return false
        }
        return super.moveTaskToBack(nonRoot)
    }

    // Handle window focus changes
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (kioskModeEnabled && hasFocus) {
            hideSystemUI()
        }
    }

    /**
     * Check if kiosk mode is currently enabled
     */
    protected fun isKioskModeEnabled(): Boolean = kioskModeEnabled
}