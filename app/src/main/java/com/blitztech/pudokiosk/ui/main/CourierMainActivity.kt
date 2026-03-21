package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityCourierMainBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.courier.CourierCollectActivity
import com.blitztech.pudokiosk.ui.courier.CourierDeliverActivity
import com.blitztech.pudokiosk.ui.courier.CourierProfileActivity
import com.blitztech.pudokiosk.ui.courier.CourierRouteActivity
import com.blitztech.pudokiosk.ui.courier.CourierStatusUpdateActivity

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

        // Show implemented cards
        binding.cardViewRoute.visibility = android.view.View.VISIBLE
        binding.cardUpdateStatus.visibility = android.view.View.VISIBLE
        binding.cardCourierProfile.visibility = android.view.View.VISIBLE
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        binding.cardPickupPackages.setOnClickListener {
            startActivity(Intent(this, CourierCollectActivity::class.java))
        }

        binding.cardDeliverPackages.setOnClickListener {
            startActivity(Intent(this, CourierDeliverActivity::class.java))
        }

        binding.cardViewRoute.setOnClickListener {
            startActivity(Intent(this, CourierRouteActivity::class.java))
        }

        binding.cardUpdateStatus.setOnClickListener {
            startActivity(Intent(this, CourierStatusUpdateActivity::class.java))
        }

        binding.cardCourierProfile.setOnClickListener {
            startActivity(Intent(this, CourierProfileActivity::class.java))
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