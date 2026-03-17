package com.blitztech.pudokiosk.ui.sendpackage

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.databinding.ActivitySendPackageBinding
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Main activity for Send Package flow
 * Uses ViewPager2 with 3 fragments and a stepper indicator
 */
class SendPackageActivity : BaseKioskActivity() {

    companion object {
        const val TAG = "SendPackageActivity"
        private const val NUM_PAGES = 3
    }

    private lateinit var binding: ActivitySendPackageBinding
    private lateinit var viewPager: ViewPager2

    // Shared data across fragments
    val sendPackageData = SendPackageData()

    // Location manager for getting sender location
    private val locationManager: LocationManager by lazy {
        getSystemService(LOCATION_SERVICE) as LocationManager
    }

    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(
                this,
                "Location permission denied. Cannot get sender location.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendPackageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupStepper()
        setupClickListeners()
        requestLocationPermission()
    }

    /**
     * Setup ViewPager2
     */
    private fun setupViewPager() {
        viewPager = binding.viewPager
        viewPager.adapter = SendPackagePagerAdapter(this)
        viewPager.isUserInputEnabled = false // Disable swipe navigation

        // Update stepper when page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateStepper(position)
            }
        })
    }

    /**
     * Setup stepper indicator
     */
    private fun setupStepper() {
        updateStepper(0)
    }

    /**
     * Update stepper UI based on current page
     */
    private fun updateStepper(position: Int) {
        // Update step indicators
        binding.step1Indicator.isActivated = position >= 0
        binding.step2Indicator.isActivated = position >= 1
        binding.step3Indicator.isActivated = position >= 2

        // Update step lines
        binding.step1Line.isActivated = position >= 1
        binding.step2Line.isActivated = position >= 2

        // Update step labels
        binding.step1Label.setTextColor(
            ContextCompat.getColor(
                this,
                if (position >= 0) R.color.zimpudo_primary else R.color.text_hint
            )
        )
        binding.step2Label.setTextColor(
            ContextCompat.getColor(
                this,
                if (position >= 1) R.color.zimpudo_primary else R.color.text_hint
            )
        )
        binding.step3Label.setTextColor(
            ContextCompat.getColor(
                this,
                if (position >= 2) R.color.zimpudo_primary else R.color.text_hint
            )
        )
    }

    /**
     * Setup click listeners
     */
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Navigate to next page
     */
    fun goToNextPage() {
        if (viewPager.currentItem < NUM_PAGES - 1) {
            viewPager.currentItem += 1
        }
    }

    /**
     * Navigate to previous page
     */
    fun goToPreviousPage() {
        if (viewPager.currentItem > 0) {
            viewPager.currentItem -= 1
        } else {
            // If on first page, show confirmation dialog
            showExitConfirmation()
        }
    }

    /**
     * Show exit confirmation dialog
     */
    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Package Sending")
            .setMessage("Are you sure you want to cancel? All entered data will be lost.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                finish()
            }
            .setNegativeButton("No, Continue", null)
            .show()
    }

    /**
     * Request location permission
     */
    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                fetchCurrentLocation()
            }
            else -> {
                // Request permission
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    /**
     * Fetch current location
     */
    private fun fetchCurrentLocation() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                location?.let {
                    sendPackageData.senderLatitude = it.latitude
                    sendPackageData.senderLongitude = it.longitude
                } ?: run {
                    // Use default location if unable to get current location
                    sendPackageData.senderLatitude = -17.8252 // Harare default
                    sendPackageData.senderLongitude = 31.0335
                    Toast.makeText(
                        this,
                        "Using default location. GPS signal not available.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            // Use default location on error
            sendPackageData.senderLatitude = -17.8252
            sendPackageData.senderLongitude = 31.0335
            Toast.makeText(this, "Location error. Using default location.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ViewPager adapter
     */
    private inner class SendPackagePagerAdapter(activity: SendPackageActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = NUM_PAGES

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> RecipientDetailsFragment()
                1 -> PackageDetailsFragment()
                2 -> PaymentFragment()
                else -> throw IllegalArgumentException("Invalid page position: $position")
            }
        }
    }
}