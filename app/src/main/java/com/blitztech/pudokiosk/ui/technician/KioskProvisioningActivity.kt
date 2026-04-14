package com.blitztech.pudokiosk.ui.technician

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.kiosk.KioskProvisionRequest
import com.blitztech.pudokiosk.ui.main.MainActivity
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
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
        private const val REQ_CODE_PERMISSIONS = 2001

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
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
    private lateinit var btnDeveloperOptions: android.widget.Button
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var tvStatus: android.widget.TextView
    private lateinit var layoutStep1: android.view.View
    private lateinit var layoutStep2: android.view.View

    private var dailyOtpCode: String = ""

    // Permission UI views
    private lateinit var tvPermCamera: android.widget.TextView
    private lateinit var tvPermLocation: android.widget.TextView
    private lateinit var tvPermStorage: android.widget.TextView
    private lateinit var btnGrantPermissions: android.widget.Button
    private lateinit var tvPermAllGranted: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply display scaling BEFORE super/setContentView (same as BaseKioskActivity)
        applyDisplayScaling()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kiosk_provisioning)

        bindViews()
        refreshPermissionStatus()
        showStep1()
    }

    override fun onResume() {
        super.onResume()
        // Ensure Kiosk lock resumes after returning from Android Settings if provisioned as DeviceOwner
        com.blitztech.pudokiosk.service.KioskLockManager.setMaintenanceMode(this, false)
        com.blitztech.pudokiosk.service.KioskLockManager.ensureLockTaskOnStartup(this)

        // Refresh permission status when returning from system settings
        if (::tvPermCamera.isInitialized) {
            refreshPermissionStatus()
        }
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
        btnDeveloperOptions = findViewById(R.id.btnDeveloperOptions)
        progressBar     = findViewById(R.id.provisioningProgressBar)
        tvStatus        = findViewById(R.id.tvProvisioningStatus)
        layoutStep1     = findViewById(R.id.layoutStep1Passcode)
        layoutStep2     = findViewById(R.id.layoutStep2Config)

        val btnAuthStep1 = findViewById<android.widget.Button>(R.id.btnAuthStep1)
        btnAuthStep1.setOnClickListener { onDailyOtpSubmit() }
        btnProvision.setOnClickListener { onCompleteProvisioning() }
        
        btnDeveloperOptions.setOnClickListener {
            val intent = Intent(this, DeveloperModeActivity::class.java)
            startActivity(intent)
        }

        val btnWifiSettings = findViewById<android.widget.Button>(R.id.btnWifiSettings)
        btnWifiSettings.setOnClickListener {
            com.blitztech.pudokiosk.service.KioskLockManager.setMaintenanceMode(this, true)
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        }

        val btnNetworkDiagnostics = findViewById<android.widget.Button>(R.id.btnNetworkDiagnostics)
        btnNetworkDiagnostics.setOnClickListener {
            val intent = Intent(this, NetworkDiagnosticsActivity::class.java)
            startActivity(intent)
        }

        // Permission views
        tvPermCamera       = findViewById(R.id.tvPermCamera)
        tvPermLocation     = findViewById(R.id.tvPermLocation)
        tvPermStorage      = findViewById(R.id.tvPermStorage)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        tvPermAllGranted   = findViewById(R.id.tvPermAllGranted)

        btnGrantPermissions.setOnClickListener { requestAllPermissions() }
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
        Toast.makeText(this, getString(R.string.auto_rem_code_captured_enter_locker_det), Toast.LENGTH_SHORT).show()
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
                        // Save ALL locker UUIDs for multi-locker cell sync
                        prefs.saveAllLockerUuids(
                            body.lockers.map { it.lockerId }
                        )

                        // Sync locker coordinates from backend (eliminates hardcoded default)
                        val primaryLocker = body.lockers.firstOrNull()
                        if (primaryLocker?.latitude != null && primaryLocker.longitude != null) {
                            prefs.setKioskLatitude(primaryLocker.latitude)
                            prefs.setKioskLongitude(primaryLocker.longitude)
                            android.util.Log.d(TAG, "Kiosk location synced from backend: " +
                                "${primaryLocker.latitude}, ${primaryLocker.longitude}")
                        } else {
                            android.util.Log.w(TAG, "⚠️ Backend locker has no coordinates — " +
                                "fare calculations will use fallback defaults until admin sets location")
                        }

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
                            getString(R.string.auto_rem_kiosk_provisioned_successfully),
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

    // ── Permission Management ────────────────────────────────────────

    private fun isPermGranted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    /**
     * Refreshes the permission status display.
     * Shows ✅ for granted, ❌ for denied.
     */
    private fun refreshPermissionStatus() {
        val camera   = isPermGranted(Manifest.permission.CAMERA)
        val location = isPermGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val storage  = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true
                       else isPermGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        tvPermCamera.text   = if (camera)   "📷 Camera: ✅ Granted" else "📷 Camera: ❌ Denied"
        tvPermLocation.text = if (location) "📍 Location: ✅ Granted" else "📍 Location: ❌ Denied"
        tvPermStorage.text  = if (storage)  "💾 Storage: ✅ Granted" else "💾 Storage: ❌ Denied"

        tvPermCamera.setTextColor(if (camera) 0xFF44DD88.toInt() else 0xFFFF6666.toInt())
        tvPermLocation.setTextColor(if (location) 0xFF44DD88.toInt() else 0xFFFF6666.toInt())
        tvPermStorage.setTextColor(if (storage) 0xFF44DD88.toInt() else 0xFFFF6666.toInt())

        val allGranted = camera && location && storage
        btnGrantPermissions.visibility = if (allGranted) View.GONE else View.VISIBLE
        tvPermAllGranted.visibility    = if (allGranted) View.VISIBLE else View.GONE
    }

    /**
     * Requests all missing dangerous permissions in a single batch.
     */
    private fun requestAllPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter { !isPermGranted(it) }
        if (missing.isEmpty()) {
            Toast.makeText(this, getString(R.string.auto_rem_all_permissions_already_grante), Toast.LENGTH_SHORT).show()
            refreshPermissionStatus()
            return
        }
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CODE_PERMISSIONS) {
            refreshPermissionStatus()

            val denied = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first.substringAfterLast('.') }
            if (denied.isEmpty()) {
                Toast.makeText(this, getString(R.string.auto_rem_all_permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Still denied: ${denied.joinToString()}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Display Scaling ─────────────────────────────────────────

    /**
     * Same density scaling as [BaseKioskActivity] so provisioning UI matches.
     */
    private fun applyDisplayScaling() {
        try {
            val manualScale = prefs.getFloat(BaseKioskActivity.KEY_DISPLAY_SCALE_OVERRIDE, 0f)
            val metrics = resources.displayMetrics

            val scaleFactor: Float = if (manualScale > 0f) {
                manualScale.coerceIn(0.75f, 1.0f)
            } else {
                val widthInches = metrics.widthPixels.toDouble() / metrics.xdpi
                val heightInches = metrics.heightPixels.toDouble() / metrics.ydpi
                val actualDiagonal = Math.sqrt(widthInches * widthInches + heightInches * heightInches)
                (actualDiagonal / 10.1).toFloat().coerceIn(0.75f, 1.0f)
            }

            if (scaleFactor < 1.0f) {
                val sysMetrics = android.content.res.Resources.getSystem().displayMetrics
                val originalDensity = sysMetrics.density
                val fontScale = sysMetrics.scaledDensity / originalDensity

                val targetDensity = originalDensity * scaleFactor
                val targetScaledDensity = targetDensity * fontScale
                val targetDensityDpi = (targetDensity * 160).toInt()

                applicationContext.resources.displayMetrics.apply {
                    density = targetDensity
                    scaledDensity = targetScaledDensity
                    densityDpi = targetDensityDpi
                }
                metrics.density = targetDensity
                metrics.scaledDensity = targetScaledDensity
                metrics.densityDpi = targetDensityDpi

                android.util.Log.d(TAG, "Display scaling applied: factor=${"%.2f".format(scaleFactor)}")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Display scaling failed — using system defaults", e)
        }
    }
}
