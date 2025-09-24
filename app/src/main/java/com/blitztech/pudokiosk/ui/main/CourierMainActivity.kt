package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.ui.main.MainActivity
import com.blitztech.pudokiosk.databinding.ActivityCourierMainBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs

/**
 * Main dashboard for courier users
 * Provides access to pickup deliveries, update status, view routes
 */
class CourierMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCourierMainBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDependencies()
        setupViews()
        setupClickListeners()
    }

    private fun setupDependencies() {
        prefs = Prefs(this)
        i18n = I18n(this)

        // Load current language
        val currentLocale = prefs.getLocale()
        i18n.load(currentLocale)
    }

    private fun setupViews() {
        binding.tvWelcome.text = i18n.t("welcome_courier", "Welcome, Courier!")
        binding.tvSubtitle.text = i18n.t("courier_subtitle", "Ready to make deliveries?")

        // Main action cards
        binding.tvPickupPackages.text = i18n.t("pickup_packages", "Pickup Packages")
        binding.tvPickupDescription.text = i18n.t("pickup_description", "Collect packages from this kiosk location")

        binding.tvDeliverPackages.text = i18n.t("deliver_packages", "Deliver Packages")
        binding.tvDeliverDescription.text = i18n.t("deliver_description", "Drop off packages at this kiosk location")

        binding.tvViewRoute.text = i18n.t("view_route", "View My Route")
        binding.tvRouteDescription.text = i18n.t("route_description", "See your assigned delivery route and schedule")

        binding.tvUpdateStatus.text = i18n.t("update_status", "Update Status")
        binding.tvStatusDescription.text = i18n.t("status_description", "Update delivery status and add notes")

        binding.tvCourierProfile.text = i18n.t("courier_profile", "My Profile")
        binding.tvProfileDescription.text = i18n.t("profile_description", "Manage your courier profile and performance")
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
            .setTitle(i18n.t("logout", "Logout"))
            .setMessage(i18n.t("logout_confirmation", "Are you sure you want to logout?"))
            .setPositiveButton(i18n.t("yes", "Yes")) { _, _ ->
                logout()
            }
            .setNegativeButton(i18n.t("no", "No"), null)
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

    fun onBackPressedDispatcher() {
        showLogoutDialog()
    }
}