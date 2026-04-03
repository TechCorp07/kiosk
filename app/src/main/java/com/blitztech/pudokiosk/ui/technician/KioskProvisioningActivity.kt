package com.blitztech.pudokiosk.ui.technician

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.kiosk.KioskProvisionRequest
import com.blitztech.pudokiosk.ui.main.MainActivity
import com.blitztech.pudokiosk.sync.SyncScheduler
import kotlinx.coroutines.launch

/**
 * KioskProvisioningActivity — first-boot setup wizard for new/factory-reset kiosks.
 *
 * Accessed when [Prefs.isProvisioned] == false, or manually from TechnicianMenuActivity.
 *
 * Steps:
 *   1. Tech enters the daily 6-digit provisioning OTP (from SYS_ADMIN dashboard)
 *   2. Tech enters up to 4 locker short codes (ZLxxxxxx, 8 chars)
 *   3. Tech enters the site name
 *   4. The kiosk calls POST /api/v1/kiosks/provision on the backend
 *   5. On success: marks kiosk as provisioned and navigates to MainActivity
 */
class KioskProvisioningActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KioskProvisioning"
    }

    private val prefs by lazy { ZimpudoApp.prefs }
    private val api by lazy { ZimpudoApp.apiRepository }

    // View references
    private lateinit var etPasscode: android.widget.EditText
    private lateinit var etLockerCode1: android.widget.EditText
    private lateinit var etLockerCode2: android.widget.EditText
    private lateinit var etLockerCode3: android.widget.EditText
    private lateinit var etLockerCode4: android.widget.EditText
    private lateinit var etSiteName: android.widget.EditText
    private lateinit var etApiUrlOverride: android.widget.EditText
    private lateinit var btnProvision: android.widget.Button
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var tvStatus: android.widget.TextView
    private lateinit var layoutStep1: android.view.View
    private lateinit var layoutStep2: android.view.View

    private var dailyOtpCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kiosk_provisioning)

        bindViews()
        showStep1()
    }

    private fun bindViews() {
        etPasscode      = findViewById(R.id.etProvisioningPasscode)
        etLockerCode1   = findViewById(R.id.etLockerCode1)
        etLockerCode2   = findViewById(R.id.etLockerCode2)
        etLockerCode3   = findViewById(R.id.etLockerCode3)
        etLockerCode4   = findViewById(R.id.etLockerCode4)
        etSiteName      = findViewById(R.id.etSiteName)
        etApiUrlOverride = findViewById(R.id.etApiUrlOverride)
        btnProvision    = findViewById(R.id.btnCompleteProvisioning)
        progressBar     = findViewById(R.id.provisioningProgressBar)
        tvStatus        = findViewById(R.id.tvProvisioningStatus)
        layoutStep1     = findViewById(R.id.layoutStep1Passcode)
        layoutStep2     = findViewById(R.id.layoutStep2Config)

        val btnAuthStep1 = findViewById<android.widget.Button>(R.id.btnAuthStep1)
        btnAuthStep1.setOnClickListener { onDailyOtpSubmit() }
        btnProvision.setOnClickListener { onCompleteProvisioning() }
    }

    private fun showStep1() {
        layoutStep1.visibility = View.VISIBLE
        layoutStep2.visibility = View.GONE
    }

    private fun showStep2() {
        layoutStep1.visibility = View.GONE
        layoutStep2.visibility = View.VISIBLE

        // Pre-fill site name if re-provisioning
        val existing = prefs.getSiteName()
        if (existing.isNotBlank() && existing != "Zimpudo Kiosk") {
            etSiteName.setText(existing)
        }
        etApiUrlOverride.setText(prefs.getApiBaseUrlOverride())
        setStatus("Enter locker short codes from your admin portal commissioning sheet.")
    }

    // ── Step 1: Save daily OTP and move to config ────────────────────
    private fun onDailyOtpSubmit() {
        val entered = etPasscode.text.toString().trim()
        if (entered.length != 6) {
            setStatus("❌ Code must be exactly 6 digits.")
            return
        }
        // Save the OTP locally — it will be sent to the backend with the provision request.
        // The backend validates it, not the kiosk.
        dailyOtpCode = entered
        Toast.makeText(this, "Code captured — enter locker details", Toast.LENGTH_SHORT).show()
        showStep2()
    }

    // ── Step 2: Provision via backend ─────────────────────────────────
    private fun onCompleteProvisioning() {
        val siteName = etSiteName.text.toString().trim()
        val apiOverride = etApiUrlOverride.text.toString().trim()

        // Collect non-empty locker codes
        val lockerCodes = listOfNotNull(
            etLockerCode1.text.toString().trim().uppercase().ifBlank { null },
            etLockerCode2.text.toString().trim().uppercase().ifBlank { null },
            etLockerCode3.text.toString().trim().uppercase().ifBlank { null },
            etLockerCode4.text.toString().trim().uppercase().ifBlank { null }
        )

        if (lockerCodes.isEmpty()) {
            setStatus("❌ At least one locker short code is required.")
            return
        }
        if (siteName.isBlank()) {
            setStatus("❌ Site name is required.")
            return
        }

        // Apply API URL override if specified (before making the provision call)
        if (apiOverride.isNotBlank()) {
            prefs.setApiBaseUrlOverride(apiOverride)
        }

        setLoading(true)
        setStatus("🔗 Provisioning kiosk with backend…")

        val request = KioskProvisionRequest(
            deviceId = prefs.getKioskDeviceId(),
            lockerShortCodes = lockerCodes,
            siteName = siteName,
            provisioningCode = dailyOtpCode,
            androidVersion = Build.VERSION.RELEASE,
            appVersion = getAppVersion(),
            serialNumber = Build.SERIAL
        )

        lifecycleScope.launch {
            val result = api.provisionKiosk(request)
            setLoading(false)

            when (result) {
                is NetworkResult.Success -> {
                    val response = result.data
                    if (response.success && response.body != null) {
                        val body = response.body
                        // Save provisioning data
                        prefs.savePrimaryLockerUuid(
                            body.lockers.firstOrNull()?.lockerId ?: ""
                        )
                        prefs.setSiteName(body.siteName)
                        prefs.setLockerCount(body.lockerCount)
                        prefs.setApiBaseUrlOverride(apiOverride)
                        prefs.setProvisioned(true)
                        // Store the kiosk backend ID
                        prefs.putString("kiosk_backend_id", body.kioskId)

                        android.util.Log.d(TAG, "Kiosk provisioned: kioskId=${body.kioskId} " +
                            "${body.lockerCount} lockers, ${body.totalCells} cells")

                        // Trigger locker sync
                        SyncScheduler.enqueueLockerSyncNow(applicationContext)

                        setStatus("✅ Kiosk provisioned: ${body.lockerCount} lockers, ${body.totalCells} cells")
                        Toast.makeText(this@KioskProvisioningActivity,
                            "✅ Kiosk provisioned successfully!",
                            Toast.LENGTH_LONG).show()

                        // Navigate to main UI
                        val intent = Intent(this@KioskProvisioningActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    } else {
                        setStatus("❌ ${response.message}")
                    }
                }
                is NetworkResult.Error -> {
                    setStatus("❌ Provisioning failed: ${result.message}\n\n" +
                        "Check that locker codes are correct and you have network connectivity.")
                }
                is NetworkResult.Loading<*> -> { }
            }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────
    private fun setLoading(loading: Boolean) = runOnUiThread {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnProvision.isEnabled = !loading
    }

    private fun setStatus(msg: String) = runOnUiThread {
        tvStatus.text = msg
        tvStatus.visibility = View.VISIBLE
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
