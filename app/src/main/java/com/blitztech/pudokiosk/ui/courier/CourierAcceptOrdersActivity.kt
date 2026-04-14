package com.blitztech.pudokiosk.ui.courier

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.OrderLookupResult
import com.blitztech.pudokiosk.databinding.ActivityCourierAcceptOrdersBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.launch

/**
 * Accept Orders Screen
 *
 * Lists orders in AWAITING_COURIER status that the courier can accept.
 * Works identically on kiosk and mobile — uses the same backend endpoints:
 *   POST /api/v1/orders/and-search { "status": "AWAITING_COURIER" }
 *   PATCH /api/v1/orders/{orderId}/bind
 */
class CourierAcceptOrdersActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierAcceptOrdersBinding
    private lateinit var prefs: Prefs
    private val api by lazy { ZimpudoApp.apiRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierAcceptOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

        binding.btnBack.setOnClickListener { finishSafely() }
        binding.btnRefresh.setOnClickListener { loadAvailableOrders() }

        loadAvailableOrders()
    }

    private fun loadAvailableOrders() {
        setLoading(true)
        binding.emptyState.visibility = View.GONE
        binding.ordersList.removeAllViews()

        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setLoading(false)
                showEmpty("Session expired. Please log in again.")
                return@launch
            }

            when (val result = api.searchAvailableOrders(token)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    val orders = result.data.content
                    if (orders.isEmpty()) {
                        showEmpty("No available orders right now.\nTap Refresh to check again.")
                    } else {
                        binding.tvSummary.text = "${orders.size} order(s) available for pickup"
                        buildOrderCards(orders)
                    }
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    Toast.makeText(
                        this@CourierAcceptOrdersActivity,
                        "Failed to load orders: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmpty("Could not load orders. Tap Refresh to try again.")
                }
                is NetworkResult.Loading<*> -> { /* handled by setLoading */ }
            }
        }
    }

    private fun buildOrderCards(orders: List<OrderLookupResult>) {
        val container = binding.ordersList
        val dp = resources.displayMetrics.density

        for (order in orders) {
            val card = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * dp).toInt()
                }
                radius = 16 * dp
                elevation = 4 * dp
                setContentPadding(
                    (20 * dp).toInt(), (16 * dp).toInt(),
                    (20 * dp).toInt(), (16 * dp).toInt()
                )
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Tracking number
            val tvTracking = TextView(this).apply {
                text = "📦  ${order.trackingNumber ?: "Unknown"}"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, 0, 0, (4 * dp).toInt())
            }
            content.addView(tvTracking)

            // Package size
            val size = order.packageDetails?.packageSize ?: "—"
            val tvSize = TextView(this).apply {
                text = "Size: $size"
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            content.addView(tvSize)

            // Distance if available
            if (!order.distance.isNullOrBlank()) {
                val tvDistance = TextView(this).apply {
                    text = "Distance: ${order.distance}"
                    textSize = 18f
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                content.addView(tvDistance)
            }

            // Accept button
            val btnAccept = com.google.android.material.button.MaterialButton(this).apply {
                text = "Accept Order"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.white))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.zimpudo_primary)
                )
                // Remove weird shadows
                elevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (64 * dp).toInt()
                ).apply {
                    topMargin = (16 * dp).toInt()
                }
                setOnClickListener {
                    isEnabled = false
                    text = "Accepting…"
                    acceptOrder(order.orderId ?: "", this, card)
                }
            }
            content.addView(btnAccept)

            card.addView(content)
            container.addView(card)
        }
    }

    private fun acceptOrder(orderId: String, button: com.google.android.material.button.MaterialButton, card: CardView) {
        if (orderId.isBlank()) {
            Toast.makeText(this, "Invalid order ID", Toast.LENGTH_SHORT).show()
            button.isEnabled = true
            button.text = "Accept Order"
            return
        }

        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            when (val result = api.acceptOrder(orderId, token)) {
                is NetworkResult.Success -> {
                    val resp = result.data
                    if (resp.success) {
                        Toast.makeText(
                            this@CourierAcceptOrdersActivity,
                            "✅ Order accepted! It's now in your route.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Visually mark as accepted
                        button.text = "✅ Accepted"
                        button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this@CourierAcceptOrdersActivity, R.color.text_hint)
                        )
                        card.alpha = 0.5f
                    } else {
                        Toast.makeText(
                            this@CourierAcceptOrdersActivity,
                            "Failed: ${resp.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        button.isEnabled = true
                        button.text = "Accept Order"
                    }
                }
                is NetworkResult.Error -> {
                    Toast.makeText(
                        this@CourierAcceptOrdersActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    button.isEnabled = true
                    button.text = "Accept Order"
                }
                is NetworkResult.Loading<*> -> { /* wait */ }
            }
        }
    }

    private fun showEmpty(msg: String) {
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = msg
        binding.tvSummary.text = ""
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
    }
}
