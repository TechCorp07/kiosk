package com.blitztech.pudokiosk.ui.courier

import com.blitztech.pudokiosk.R

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.blitztech.pudokiosk.databinding.ActivityCourierStatusUpdateBinding
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Courier Status Update / Problem Reporting Screen
 *
 * Allows a courier to report an issue with a parcel (damaged, wrong address,
 * refused delivery) without performing a full delivery or collection scan.
 *
 * Flow:
 * 1. Courier scans the parcel barcode (or types tracking number manually)
 * 2. Selects an issue type from the spinner
 * 3. Taps "Report" → saves to local outbox for background sync
 */
class CourierStatusUpdateActivity : BaseKioskActivity() {

    companion object {
        private const val TAG = "CourierStatusUpdate"
    }

    private lateinit var binding: ActivityCourierStatusUpdateBinding
    private lateinit var prefs: Prefs
    private var scanJob: Job? = null

    private val issueTypes = listOf(
        "Damaged parcel",
        "Wrong address / recipient not found",
        "Recipient refused delivery",
        "Parcel not in locker",
        "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierStatusUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

        setupSpinner()
        setupClickListeners()
        startScanListening()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, issueTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerIssueType.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finishSafely() }

        binding.btnReport.setOnClickListener {
            val tracking = binding.etTrackingNumber.text.toString().trim()
            if (tracking.isEmpty()) {
                binding.tilTrackingNumber.error = getString(R.string.auto_rem_please_scan_or_enter_a_trackin)
                return@setOnClickListener
            }
            binding.tilTrackingNumber.error = null
            submitReport(tracking)
        }
    }

    private fun startScanListening() {
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            BarcodeScanner.scannedData.collect { scannedCode ->
                binding.etTrackingNumber.setText(scannedCode)
                binding.tilTrackingNumber.error = null
                showToast("Scanned: $scannedCode")
            }
        }
    }

    /**
     * Save the issue report to the local outbox for background sync.
     * This avoids misusing the courier/dropoff endpoint which would create
     * real delivery records and assign locker cells.
     */
    private fun submitReport(trackingNumber: String) {
        setLoading(true)
        val issue = issueTypes[binding.spinnerIssueType.selectedItemPosition]
        val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }

        lifecycleScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("trackingNumber", trackingNumber)
                    put("kioskId", kioskId)
                    put("issueType", issue)
                    put("reportedBy", prefs.getUserMobile() ?: "unknown")
                    put("timestamp", System.currentTimeMillis())
                }

                val event = OutboxEventEntity(
                    idempotencyKey = "issue_${trackingNumber}_${System.currentTimeMillis()}",
                    type = "courier_issue_report",
                    payloadJson = payload.toString(),
                    createdAt = System.currentTimeMillis()
                )

                ZimpudoApp.database.outbox().enqueue(event)

                setLoading(false)
                showStatus(
                    "✅ Report saved for $trackingNumber\n" +
                        "Issue: $issue\n" +
                        "Will sync to server when online."
                )
                binding.etTrackingNumber.text?.clear()
                Log.d(TAG, "Issue report saved: $trackingNumber — $issue")
            } catch (e: Exception) {
                setLoading(false)
                showStatus("⚠ Failed to save report: ${e.message}")
                Log.e(TAG, "Failed to save issue report", e)
            }
        }
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.text = msg
        binding.tvStatus.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnReport.isEnabled = !loading
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
    }
}
