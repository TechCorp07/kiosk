package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.databinding.ActivityCustomerMainBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.collect.CollectionCodeActivity
import com.blitztech.pudokiosk.ui.customer.CustomerAccountActivity
import com.blitztech.pudokiosk.ui.customer.TrackDeliveryActivity
import com.blitztech.pudokiosk.ui.sendpackage.SendPackageActivity
import kotlinx.coroutines.launch

/**
 * Main dashboard for customer users
 * Provides access to send packages, collect packages, track deliveries
 */
class CustomerMainActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCustomerMainBinding
    private lateinit var prefs: Prefs
    private val api by lazy { ZimpudoApp.apiRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDependencies()
        setupViews()
        setupClickListeners()
    }

    private fun setupDependencies() {
        prefs = ZimpudoApp.prefs
    }

    override fun onResume() {
        super.onResume()
        checkPendingReservations()
    }

    private fun checkPendingReservations() {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: return@launch
            val senderMobile = prefs.getUserMobile() ?: return@launch
            try {
                // Spec Section 4 Step 2.5:
                // Use POST /orders/and-search with senderMobileNumber
                // (accepts USER + API roles — more reliable than /orders/logged-in)
                val result = api.searchOrdersBySender(senderMobile, token)
                if (result is NetworkResult.Success) {
                    val page = result.data
                    // Backend OrderStatus values for pending drop-offs:
                    //   LOCKER_RESERVED  → cell allocated, awaiting deposit
                    //   AWAITING_DEPOSIT → Barcode generated, awaiting physical drop-off
                    //   AWAITING_COURIER → payment done, parcel ready for courier pickup
                    val dropOffStatuses = setOf("LOCKER_RESERVED", "AWAITING_DEPOSIT", "AWAITING_COURIER")
                    val pendingOrder = page.content.firstOrNull { it.status in dropOffStatuses }
                    
                    if (pendingOrder != null) {
                        binding.cardDropoffReserved.visibility = android.view.View.VISIBLE
                        binding.cardDropoffReserved.setOnClickListener {
                            val intent = Intent(this@CustomerMainActivity, SendPackageActivity::class.java)
                            intent.putExtra("RESERVED_TRACKING", pendingOrder.trackingNumber)
                            startActivity(intent)
                        }
                    } else {
                        binding.cardDropoffReserved.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerMainActivity", "Error checking reservations", e)
            }
        }
    }

    private fun setupViews() {
        binding.tvWelcome.text = getString(R.string.welcome_customer)
        binding.tvSubtitle.text = getString(R.string.customer_subtitle)

        // Main action cards
        binding.tvSendPackage.text = getString(R.string.send_package)
        binding.tvSendDescription.text = getString(R.string.send_description)

        binding.tvTrackDelivery.text = getString(R.string.track_delivery)
        binding.tvTrackDescription.text = getString(R.string.track_description)

        binding.tvMyAccount.text = getString(R.string.my_account)
        binding.tvAccountDescription.text = getString(R.string.account_description)
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        binding.cardSendPackage.setOnClickListener {
            val intent = Intent(this, SendPackageActivity::class.java)
            startActivity(intent)
        }

        binding.cardTrackDelivery.setOnClickListener {
            startActivity(Intent(this, TrackDeliveryActivity::class.java))
        }

        binding.cardMyAccount.setOnClickListener {
            startActivity(Intent(this, CustomerAccountActivity::class.java))
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

        // Return to sign-in page for customers
        val intent = Intent(this, com.blitztech.pudokiosk.ui.auth.SignInActivity::class.java).apply {
            putExtra(com.blitztech.pudokiosk.ui.auth.SignInActivity.EXTRA_USER_TYPE, "CUSTOMER")
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
