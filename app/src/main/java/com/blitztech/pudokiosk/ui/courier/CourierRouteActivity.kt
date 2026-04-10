package com.blitztech.pudokiosk.ui.courier

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.databinding.ActivityCourierRouteBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.launch

/**
 * Courier Route / Today's Manifest Screen
 *
 * Fetches the courier's active orders for the session via:
 *   GET /api/v1/orders/couriers  (via getCourierOrders from ApiRepository)
 *
 * Displays a summary list of pending deliveries assigned to this courier.
 * This gives the courier a quick view of how many parcels to collect/deliver
 * before stepping up to a scan session.
 */
class CourierRouteActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierRouteBinding
    private lateinit var prefs: Prefs
    private val api by lazy { ZimpudoApp.apiRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

        binding.btnBack.setOnClickListener { finishSafely() }
        binding.btnRefresh.setOnClickListener { loadRoute() }
        loadRoute()
    }

    private fun loadRoute() {
        setLoading(true)
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setLoading(false)
                showEmpty("Session expired. Please log in again.")
                return@launch
            }

            when (val result = api.getCourierOrders(token)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    val orders = result.data.content
                    if (orders.isEmpty()) {
                        showEmpty("No active deliveries assigned.")
                    } else {
                        // Build a plain text summary — no custom adapter needed
                        val sb = StringBuilder()
                        orders.forEachIndexed { idx, order ->
                            sb.appendLine("${idx + 1}. Tracking: ${order.trackingNumber ?: "—"}")
                            sb.appendLine("   Status: ${order.status ?: "UNKNOWN"}")
                            order.cellNumber?.let { sb.appendLine("   Cell: $it") }
                            sb.appendLine()
                        }
                        binding.tvRouteContent.text = sb.toString()
                        binding.tvRouteContent.visibility = View.VISIBLE
                        binding.tvEmpty.visibility = View.GONE
                        binding.tvSummary.text = "${orders.size} parcel(s) in today's route"
                    }
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    Toast.makeText(this@CourierRouteActivity,
                        "Failed to load route: ${result.message}", Toast.LENGTH_LONG).show()
                    showEmpty("Could not load route. Tap Refresh to try again.")
                }
                is NetworkResult.Loading<*> -> { /* handled by setLoading */ }
            }
        }
    }

    private fun showEmpty(msg: String) {
        binding.tvEmpty.visibility = View.VISIBLE
        // tvEmpty is a LinearLayout; update its child TextView with the message
        val tv = binding.tvEmpty.getChildAt(0)
        if (tv is android.widget.TextView) tv.text = msg
        binding.tvRouteContent.visibility = View.GONE
        binding.tvSummary.text = ""
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
    }
}
