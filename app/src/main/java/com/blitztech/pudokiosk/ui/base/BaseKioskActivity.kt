package com.blitztech.pudokiosk.ui.base

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Base activity for all kiosk activities with proper back button handling
 */
abstract class BaseKioskActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BaseKioskActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackButtonHandling()
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
        val parentActivityName = packageManager.getActivityInfo(componentName, 0)
            .parentActivityName

        if (parentActivityName != null) {
            try {
                val parentClass = Class.forName(parentActivityName)
                val intent = Intent(this, parentClass)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                Log.d(TAG, "Navigating back to: $parentActivityName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to navigate to parent activity: $parentActivityName", e)
                navigateToMain()
            }
        } else {
            // No parent defined, go to main
            navigateToMain()
        }
    }

    /**
     * Navigate to main activity (fallback)
     */
    protected fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        Log.d(TAG, "Navigating back to MainActivity")
    }

    /**
     * Finish this activity properly
     */
    protected fun finishActivity() {
        finish()
    }
}