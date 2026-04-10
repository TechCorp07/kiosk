package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.OrderLookupResult
import com.blitztech.pudokiosk.databinding.ActivityCustomerMainBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.collect.CollectionCodeActivity
import com.blitztech.pudokiosk.ui.customer.CustomerAccountActivity
import com.blitztech.pudokiosk.ui.customer.TrackDeliveryActivity
import com.blitztech.pudokiosk.ui.sendpackage.SendPackageActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        checkPendingOrders()
    }

    /**
     * Check for any pending orders that need attention:
     * - AWAITING_PAYMENT → offer to resume payment or cancel
     * - LOCKER_RESERVED / AWAITING_DEPOSIT / AWAITING_COURIER → offer to complete drop-off
     */
    private fun checkPendingOrders() {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: return@launch
            val senderMobile = prefs.getUserMobile() ?: return@launch
            try {
                val result = api.searchOrdersBySender(senderMobile, token)
                if (result is NetworkResult.Success) {
                    val page = result.data

                    // 1. Check for unpaid orders (AWAITING_PAYMENT)
                    val unpaidOrder = page.content.firstOrNull { it.status == "AWAITING_PAYMENT" }
                    if (unpaidOrder != null) {
                        showPendingPaymentDialog(unpaidOrder)
                        return@launch
                    }

                    // 2. Check for orders needing physical drop-off
                    val dropOffStatuses = setOf("LOCKER_RESERVED", "AWAITING_DEPOSIT", "AWAITING_COURIER")
                    val pendingOrder = page.content.firstOrNull { it.status in dropOffStatuses }

                    if (pendingOrder != null) {
                        binding.cardDropoffReserved.visibility = android.view.View.VISIBLE
                        binding.cardDropoffReserved.setOnClickListener {
                            val intent = Intent(this@CustomerMainActivity, SendPackageActivity::class.java).apply {
                                putExtra("DROP_RESERVED_ORDER_ID", pendingOrder.orderId)
                                putExtra("DROP_RESERVED_TRACKING", pendingOrder.trackingNumber)
                                putExtra("DROP_RESERVED_LOCKER_ID", pendingOrder.cabinetId ?: "")
                                putExtra("DROP_RESERVED_CELL_ID", pendingOrder.cellId ?: "")
                            }
                            startActivity(intent)
                        }
                    } else {
                        binding.cardDropoffReserved.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerMainActivity", "Error checking pending orders", e)
            }
        }
    }

    /**
     * Show a kiosk-sized dialog for unpaid orders with Resume / Cancel / Later options.
     */
    private fun showPendingPaymentDialog(order: OrderLookupResult) {
        val currencySymbol = when (order.currency ?: "USD") {
            "USD" -> "$"
            "ZWG" -> "ZWG "
            else -> ""
        }
        val amountText = if (order.price != null) {
            "${currencySymbol}${String.format("%.2f", order.price)}"
        } else {
            "amount pending"
        }
        val recipientText = order.recipientId ?: "N/A"

        // Use custom kiosk layout for larger text
        val dialogView = layoutInflater.inflate(R.layout.dialog_pending_order, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnLater = dialogView.findViewById<Button>(R.id.btnLater)
        val btnCancelOrder = dialogView.findViewById<Button>(R.id.btnCancelOrder)
        val btnResumePayment = dialogView.findViewById<Button>(R.id.btnResumePayment)

        tvTitle.text = "📦 Pending Order Found"
        tvMessage.text = "You have an unpaid order that needs attention:\n\n" +
                "Tracking: ${order.trackingNumber ?: "N/A"}\n" +
                "Recipient: $recipientText\n" +
                "Amount: $amountText\n" +
                "Status: Awaiting Payment\n\n" +
                "Would you like to resume payment or cancel this order?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnLater.setOnClickListener { dialog.dismiss() }
        btnCancelOrder.setOnClickListener {
            dialog.dismiss()
            confirmCancelOrder(order)
        }
        btnResumePayment.setOnClickListener {
            dialog.dismiss()
            resumeOrderPayment(order)
        }

        dialog.show()
    }

    /**
     * Navigate to SendPackageActivity with RESUME extras.
     * The activity will detect these and skip directly to the Payment page.
     */
    private fun resumeOrderPayment(order: OrderLookupResult) {
        // cabinetId is not set on AWAITING_PAYMENT orders (assigned during payment flow).
        // Fall back to this kiosk's own locker ID — the user is physically here.
        val lockerId = order.cabinetId?.takeIf { it.isNotBlank() }
            ?: prefs.getPrimaryLockerUuid()

        val intent = Intent(this, SendPackageActivity::class.java).apply {
            putExtra("RESUME_ORDER_ID", order.orderId)
            putExtra("RESUME_TRACKING", order.trackingNumber)
            putExtra("RESUME_AMOUNT", order.price ?: 0.0)
            putExtra("RESUME_CURRENCY", order.currency ?: "USD")
            putExtra("RESUME_RECIPIENT", order.recipientId ?: "")
            putExtra("RESUME_LOCKER_ID", lockerId)
            putExtra("RESUME_DISTANCE", order.distance ?: "")
            putExtra("RESUME_PACKAGE_SIZE", order.packageDetails?.packageSize ?: "")
        }
        startActivity(intent)
    }

    /**
     * Confirm before cancelling — this is destructive.
     */
    private fun confirmCancelOrder(order: OrderLookupResult) {
        // Use custom dialog for kiosk sizing
        val dialogView = layoutInflater.inflate(R.layout.dialog_pending_order, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnLater = dialogView.findViewById<Button>(R.id.btnLater)
        val btnCancelOrder = dialogView.findViewById<Button>(R.id.btnCancelOrder)
        val btnResumePayment = dialogView.findViewById<Button>(R.id.btnResumePayment)

        tvTitle.text = "⚠️ Confirm Cancellation"
        tvMessage.text = "Are you sure you want to cancel this order?\n\n" +
                "Tracking: ${order.trackingNumber ?: "N/A"}\n\n" +
                "This action cannot be undone."

        btnLater.text = "No, Keep Order"
        btnCancelOrder.text = "Yes, Cancel Order"
        btnResumePayment.visibility = android.view.View.GONE

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnLater.setOnClickListener { dialog.dismiss() }
        btnCancelOrder.setOnClickListener {
            dialog.dismiss()
            executeCancelOrder(order)
        }

        dialog.show()
    }

    /**
     * Call backend PATCH /api/v1/orders/{orderId}/cancel
     */
    private fun executeCancelOrder(order: OrderLookupResult) {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: return@launch
            val orderId = order.orderId ?: return@launch

            try {
                val result = api.cancelOrder(orderId, token)
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(
                            this@CustomerMainActivity,
                            "Order cancelled successfully.",
                            Toast.LENGTH_SHORT
                        ).show()
                        checkPendingOrders()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            this@CustomerMainActivity,
                            "Failed to cancel order: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is NetworkResult.Loading<*> -> { /* no-op */ }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@CustomerMainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupViews() {
        binding.tvWelcome.text = getString(R.string.welcome_customer)
        binding.tvSubtitle.text = getString(R.string.customer_subtitle)

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
        prefs.clearAuthData()
        val intent = Intent(this, com.blitztech.pudokiosk.ui.auth.SignInActivity::class.java).apply {
            putExtra(com.blitztech.pudokiosk.ui.auth.SignInActivity.EXTRA_USER_TYPE, "CUSTOMER")
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
