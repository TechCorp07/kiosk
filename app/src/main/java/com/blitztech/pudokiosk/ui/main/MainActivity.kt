package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
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

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetectorCompat

    private var tapCount = 0
    private var lastTapTime = 0L
    private val techAccessTapCount = 7  // 7 rapid taps for tech access
    private val tapTimeoutMs = 1000L    // Reset tap count after 1 second

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTechnicianAccess()

        // Auto-navigate to language selection after 3 seconds
        binding.root.postDelayed({
            navigateToLanguageSelection()
        }, 3000)

        // Allow manual tap to skip splash
        binding.root.setOnClickListener {
            navigateToLanguageSelection()
        }
    }

    private fun setupTechnicianAccess() {
        gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val currentTime = System.currentTimeMillis()

                    // Reset tap count if too much time passed
                    if (currentTime - lastTapTime > tapTimeoutMs) {
                        tapCount = 0
                    }

                    tapCount++
                    lastTapTime = currentTime

                    // Check if we reached the tech access tap count
                    if (tapCount >= techAccessTapCount) {
                        navigateToTechnicianAccess()
                        return true
                    }

                    return false
                }
            })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun navigateToLanguageSelection() {
        val intent = Intent(this, LanguageSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToTechnicianAccess() {
        val intent = Intent(this, TechnicianAccessActivity::class.java)
        startActivity(intent)
        finish()
    }
}