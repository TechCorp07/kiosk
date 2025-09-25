package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityCourierMainBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity

/**
 * Main dashboard for courier users
 * Provides access to pickup deliveries, update status, view routes
 */
class CourierMainActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDependencies()
        setupViews()
        setupClickListeners()
    }

    private fun setupDependencies() {
        prefs = ZimpudoApp.prefs
    }

    private fun setupViews() {
        binding.tvWelcome.text = getString(R.string.welcome_courier)
        binding.tvSubtitle.text = getString(R.string.courier_subtitle)

        // Main action cards
        binding.tvPickupPackages.text = getString(R.string.pickup_packages)
        binding.tvPickupDescription.text = getString(R.string.pickup_description)

        binding.tvDeliverPackages.text = getString(R.string.deliver_packages)
        binding.tvDeliverDescription.text = getString(R.string.deliver_description)

        binding.tvViewRoute.text = getString(R.string.view_route)
        binding.tvRouteDescription.text = getString(R.string.route_description)

        binding.tvUpdateStatus.text = getString(R.string.update_status)
        binding.tvStatusDescription.text = getString(R.string.status_description)

        binding.tvCourierProfile.text = getString(R.string.courier_profile)
        binding.tvProfileDescription.text = getString(R.string.profile_description)
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        binding.cardViewRoute.setOnClickListener {
            // TODO: Navigate to route view
            // startActivity(Intent(this, RouteViewActivity::class.java))
        }

        binding.cardUpdateStatus.setOnClickListener {
            // TODO: Navigate to status update
            // startActivity(Intent(this, StatusUpdateActivity::class.java))
        }

        binding.cardCourierProfile.setOnClickListener {
            // TODO: Navigate to courier profile
            // startActivity(Intent(this, CourierProfileActivity::class.java))
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                logout()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun logout() {
        // Clear any stored session data
        prefs.clearAuthData()

        // Return to main app flow
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}