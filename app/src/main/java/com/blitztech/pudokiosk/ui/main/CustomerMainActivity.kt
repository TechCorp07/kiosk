package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.databinding.ActivityCustomerMainBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs

/**
 * Main dashboard for customer users
 * Provides access to send packages, collect packages, track deliveries
 */
class CustomerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMainBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fun setupBackPressHandling() {
            onBackPressedDispatcher.addCallback(this) {
                finish()
            }
        }
        setupDependencies()
        setupViews()
        setupClickListeners()
        setupBackPressHandling()
    }

    private fun setupDependencies() {
        prefs = Prefs(this)
        i18n = I18n(this)

        // Load current language
        val currentLocale = prefs.getLocale()
        i18n.load(currentLocale)
    }

    private fun setupViews() {
        binding.tvWelcome.text = i18n.t("welcome_customer", "Welcome, Customer!")
        binding.tvSubtitle.text = i18n.t("customer_subtitle", "What would you like to do today?")

        // Main action cards
        binding.tvSendPackage.text = i18n.t("send_package", "Send Package")
        binding.tvSendDescription.text = i18n.t("send_description", "Send packages nationwide with secure delivery")

        binding.tvCollectPackage.text = i18n.t("collect_package", "Collect Package")
        binding.tvCollectDescription.text = i18n.t("collect_description", "Collect packages waiting for you at this location")

        binding.tvTrackDelivery.text = i18n.t("track_delivery", "Track Delivery")
        binding.tvTrackDescription.text = i18n.t("track_description", "Track your packages in real-time")

        binding.tvMyAccount.text = i18n.t("my_account", "My Account")
        binding.tvAccountDescription.text = i18n.t("account_description", "Manage your profile and delivery history")
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
            finish()
        }

        binding.cardSendPackage.setOnClickListener {
            // TODO: Navigate to send package flow
            //startActivity(Intent(this, SenderActivity::class.java))
        }

        binding.cardCollectPackage.setOnClickListener {
            // TODO: Navigate to collect package flow
            //startActivity(Intent(this, RecipientActivity::class.java))
        }

        binding.cardTrackDelivery.setOnClickListener {
            // TODO: Navigate to tracking screen
            // startActivity(Intent(this, TrackingActivity::class.java))
        }

        binding.cardMyAccount.setOnClickListener {
            // TODO: Navigate to account management
            // startActivity(Intent(this, AccountActivity::class.java))
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
}