package com.blitztech.pudokiosk.ui.customer

import com.blitztech.pudokiosk.R

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.customer.CustomerParcel
import com.blitztech.pudokiosk.databinding.ActivityTrackDeliveryBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.launch

/**
 * Customer Parcel Tracking Screen
 *
 * Uses GET /api/v1/orders/logged-in (JWT auth, returns PageOrder).
 * Maps OrderDto to CustomerParcel for display via ParcelStatusAdapter.
 */
class TrackDeliveryActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityTrackDeliveryBinding
    private lateinit var prefs: Prefs
    private val api by lazy { ZimpudoApp.apiRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackDeliveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

        binding.btnBack.setOnClickListener { finishSafely() }
        
        binding.btnSearchTracking.setOnClickListener {
            val trackingNum = binding.etSearchTracking.text.toString().trim()
            if (trackingNum.isNotEmpty()) {
                showTrackingDetails(trackingNum)
            } else {
                Toast.makeText(this, getString(R.string.auto_rem_please_enter_a_tracking_number), Toast.LENGTH_SHORT).show()
            }
        }
        
        loadParcels()
    }

    private fun loadParcels() {
        setLoading(true)
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setLoading(false)
                showEmpty()
                return@launch
            }
            val result = api.getLoggedInOrders(token)
            setLoading(false)

            when (result) {
                is NetworkResult.Success -> {
                    val orders = result.data.content
                    if (orders.isEmpty()) {
                        showEmpty()
                    } else {
                        // Map OrderDto → CustomerParcel for the existing adapter
                        val parcels = orders.map { order ->
                            CustomerParcel(
                                parcelId = order.id ?: "",
                                trackingCode = order.trackingNumber ?: "—",
                                status = order.status ?: "UNKNOWN",
                                lockNumber = order.cellNumber,
                                parcelSize = order.parcelSize ?: "MEDIUM",
                                senderName = order.senderName,
                                updatedAt = order.updatedAt ?: order.createdAt ?: ""
                            )
                        }
                        showParcels(parcels)
                    }
                }
                is NetworkResult.Error -> {
                    Toast.makeText(
                        this@TrackDeliveryActivity,
                        "Failed to load orders: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmpty()
                }
                is NetworkResult.Loading<*> -> { /* handled via setLoading */ }
            }
        }
    }

    private fun showParcels(parcels: List<CustomerParcel>) {
        binding.tvEmpty.visibility = View.GONE
        binding.rvParcels.visibility = View.VISIBLE
        binding.rvParcels.layoutManager = LinearLayoutManager(this)
        binding.rvParcels.adapter = ParcelStatusAdapter(parcels) { parcel ->
            showTrackingDetails(parcel.trackingCode)
        }
    }

    private fun showTrackingDetails(trackingNumber: String) {
        val dialog = ParcelDetailsDialog(this, trackingNumber, api)
        dialog.show()
    }

    private fun showEmpty() {
        binding.tvEmpty.visibility = View.VISIBLE
        binding.rvParcels.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
