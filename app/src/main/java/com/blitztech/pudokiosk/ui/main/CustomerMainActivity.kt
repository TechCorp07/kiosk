package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityCustomerMainBinding
import com.blitztech.pudokiosk.prefs.Prefs

/**
 * Main dashboard for customer users
 * Provides access to send packages, collect packages, track deliveries
 */
class CustomerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMainBinding
    private lateinit var prefs: Prefs

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
        prefs = ZimpudoApp.prefs
    }

    private fun setupViews() {
        binding.tvWelcome.text = getString(R.string.welcome_customer)
        binding.tvSubtitle.text = getString(R.string.customer_subtitle)

        // Main action cards
        binding.tvSendPackage.text = getString(R.string.send_package)
        binding.tvSendDescription.text = getString(R.string.send_description)

        binding.tvCollectPackage.text = getString(R.string.collect_package)
        binding.tvCollectDescription.text = getString(R.string.collect_description)

        binding.tvTrackDelivery.text = getString(R.string.track_delivery)
        binding.tvTrackDescription.text = getString(R.string.track_description)

        binding.tvMyAccount.text = getString(R.string.my_account)
        binding.tvAccountDescription.text = getString(R.string.account_description)
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