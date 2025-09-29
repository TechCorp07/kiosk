package com.blitztech.pudokiosk.ui.base

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Enhanced Base Activity for maintaining TRUE KIOSK MODE across all app activities
 * All activities in the kiosk flow should extend this class
 */
abstract class BaseKioskActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BaseKioskActivity"
        private const val UI_HIDE_DELAY = 3000L // Hide UI after 3 seconds
    }

    private val uiHideHandler = Handler(Looper.getMainLooper())
    private var kioskModeEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup kiosk mode for this activity
        setupKioskMode()
        setupBackButtonHandling()
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
    protected open fun handleBackNavigation() {
        // Get the parent activity from manifest
        val parentActivityName = try {
            packageManager.getActivityInfo(componentName, 0).parentActivityName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting parent activity info", e)
            null
        }

        if (parentActivityName != null) {
            try {
                val parentClass = Class.forName(parentActivityName)
                val intent = Intent(this, parentClass).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Log.d(TAG, "Navigating back to: $parentActivityName")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to navigate to parent activity: $parentActivityName", e)
            }
        }

        // Fallback: go to main activity
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

        Log.d(TAG, "${this::class.java.simpleName} resumed in kiosk mode")
    }

    override fun onPause() {
        super.onPause()
        uiHideHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHideHandler.removeCallbacksAndMessages(null)
    }

    // Override to prevent activity from finishing in kiosk mode
    override fun finish() {
        if (kioskModeEnabled && !isFinishing) {
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