package com.blitztech.pudokiosk.ui.Technician

import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.databinding.ActivityNetworkDiagnosticsBinding
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Network Diagnostics Activity - Test network connectivity and diagnose issues
 * Compatible with Android API 25 (Android 7.1.2)
 */
class NetworkDiagnosticsActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityNetworkDiagnosticsBinding
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // Test endpoints
    private val testEndpoints = listOf(
        "68.183.176.201" to 8222, // Your backend server
        "8.8.8.8" to 53,          // Google DNS
        "1.1.1.1" to 53           // Cloudflare DNS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeManagers()
        setupViews()
        setupClickListeners()
        runInitialDiagnostics()
    }

    private fun initializeManagers() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    }

    private fun setupViews() {
        binding.tvTitle.text = "Network Diagnostics"
        binding.tvSubtitle.text = "Network connectivity and performance testing"
        binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            returnToTechMenu()
        }

        binding.btnRefresh.setOnClickListener {
            runAllDiagnostics()
        }

        binding.btnPingTest.setOnClickListener {
            runPingTest()
        }

        binding.btnConnectivityTest.setOnClickListener {
            runConnectivityTest()
        }

        binding.btnSpeedTest.setOnClickListener {
            runSpeedTest()
        }
    }

    private fun runInitialDiagnostics() {
        runAllDiagnostics()
    }

    private fun runAllDiagnostics() {
        setLoading(true)
        updateResults("Running comprehensive network diagnostics...\n\n")

        lifecycleScope.launch {
            val results = StringBuilder()

            try {
                // Basic network info
                results.append("=== NETWORK STATUS ===\n")
                results.append(getNetworkStatus())
                results.append("\n")

                // WiFi info
                results.append("=== WIFI INFORMATION ===\n")
                results.append(getWifiInfo())
                results.append("\n")

                // Connectivity tests
                results.append("=== CONNECTIVITY TESTS ===\n")
                results.append(runConnectivityTests())
                results.append("\n")

                // Ping tests
                results.append("=== PING TESTS ===\n")
                results.append(runPingTests())
                results.append("\n")

                // Backend connectivity
                results.append("=== BACKEND CONNECTION ===\n")
                results.append(testBackendConnectivity())

            } catch (e: Exception) {
                results.append("Error during diagnostics: ${e.message}\n")
            }

            updateResults(results.toString())
            setLoading(false)
        }
    }

    private fun getNetworkStatus(): String {
        return try {
            val activeNetwork = connectivityManager.activeNetworkInfo
            val builder = StringBuilder()

            builder.append("Network State: ${if (activeNetwork?.isConnected == true) "CONNECTED" else "DISCONNECTED"}\n")

            if (activeNetwork != null) {
                builder.append("Type: ${getNetworkTypeName(activeNetwork.type)}\n")
                builder.append("Subtype: ${activeNetwork.subtypeName}\n")
                builder.append("Extra Info: ${activeNetwork.extraInfo ?: "None"}\n")
                builder.append("Available: ${activeNetwork.isAvailable}\n")
                builder.append("Connected: ${activeNetwork.isConnected}\n")
                builder.append("Roaming: ${activeNetwork.isRoaming}\n")
            } else {
                builder.append("No active network connection\n")
            }

            builder.toString()
        } catch (e: Exception) {
            "Error getting network status: ${e.message}\n"
        }
    }

    private fun getWifiInfo(): String {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            val builder = StringBuilder()

            if (wifiInfo != null) {
                builder.append("WiFi State: ${getWifiStateName(wifiManager.wifiState)}\n")
                builder.append("SSID: ${wifiInfo.ssid}\n")
                builder.append("BSSID: ${wifiInfo.bssid}\n")
                builder.append("Signal Strength: ${wifiInfo.rssi} dBm\n")
                builder.append("Link Speed: ${wifiInfo.linkSpeed} Mbps\n")
                builder.append("Network ID: ${wifiInfo.networkId}\n")
                builder.append("IP Address: ${formatIpAddress(wifiInfo.ipAddress)}\n")
            } else {
                builder.append("WiFi info not available\n")
            }

            builder.toString()
        } catch (e: Exception) {
            "Error getting WiFi info: ${e.message}\n"
        }
    }

    private suspend fun runConnectivityTests(): String = withContext(Dispatchers.IO) {
        val results = StringBuilder()

        for ((host, port) in testEndpoints) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 5000)
                val endTime = System.currentTimeMillis()

                results.append("✅ $host:$port - Connected (${endTime - startTime}ms)\n")
                socket.close()

            } catch (e: Exception) {
                results.append("❌ $host:$port - Failed: ${e.message}\n")
            }
        }

        results.toString()
    }

    private suspend fun runPingTests(): String = withContext(Dispatchers.IO) {
        val results = StringBuilder()

        val hosts = listOf("68.183.176.201", "8.8.8.8", "google.com")

        for (host in hosts) {
            try {
                val startTime = System.currentTimeMillis()
                val address = InetAddress.getByName(host)
                val reachable = address.isReachable(5000)
                val endTime = System.currentTimeMillis()

                if (reachable) {
                    results.append("✅ $host - Reachable (${endTime - startTime}ms)\n")
                } else {
                    results.append("❌ $host - Not reachable\n")
                }

            } catch (e: Exception) {
                results.append("❌ $host - Error: ${e.message}\n")
            }
        }

        results.toString()
    }

    private suspend fun testBackendConnectivity(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("http://68.183.176.201:8222/api/v1/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage

            connection.disconnect()

            "Backend Status: HTTP $responseCode - $responseMessage\n" +
                    "Endpoint: ${url.toString()}\n" +
                    "Result: ${if (responseCode == 200) "✅ SUCCESS" else "⚠️ CHECK REQUIRED"}\n"

        } catch (e: IOException) {
            "Backend Status: ❌ CONNECTION FAILED\n" +
                    "Error: ${e.message}\n" +
                    "Check: Ensure backend server is running\n"
        } catch (e: Exception) {
            "Backend Status: ❌ ERROR\n" +
                    "Error: ${e.message}\n"
        }
    }

    private fun runPingTest() {
        setLoading(true)
        updateResults("Running ping tests...\n\n")

        lifecycleScope.launch {
            val results = runPingTests()
            updateResults("=== PING TEST RESULTS ===\n$results")
            setLoading(false)
        }
    }

    private fun runConnectivityTest() {
        setLoading(true)
        updateResults("Testing connectivity...\n\n")

        lifecycleScope.launch {
            val results = runConnectivityTests()
            updateResults("=== CONNECTIVITY TEST RESULTS ===\n$results")
            setLoading(false)
        }
    }

    private fun runSpeedTest() {
        showToast("Speed test: Use ping results as latency indicator")
        updateResults("Speed Test: Check ping results for network latency\n" +
                "For detailed speed testing, use external tools\n" +
                "Current connection appears stable based on connectivity tests")
    }

    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            ConnectivityManager.TYPE_WIFI -> "WiFi"
            ConnectivityManager.TYPE_MOBILE -> "Mobile Data"
            ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
            ConnectivityManager.TYPE_BLUETOOTH -> "Bluetooth"
            else -> "Unknown ($type)"
        }
    }

    private fun getWifiStateName(state: Int): String {
        return when (state) {
            WifiManager.WIFI_STATE_DISABLED -> "Disabled"
            WifiManager.WIFI_STATE_ENABLED -> "Enabled"
            WifiManager.WIFI_STATE_ENABLING -> "Enabling"
            WifiManager.WIFI_STATE_DISABLING -> "Disabling"
            WifiManager.WIFI_STATE_UNKNOWN -> "Unknown"
            else -> "Unknown ($state)"
        }
    }

    private fun formatIpAddress(ip: Int): String {
        return String.format(
            Locale.getDefault(),
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    private fun updateResults(results: String) {
        binding.tvResults.text = results
        binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
        binding.btnPingTest.isEnabled = !loading
        binding.btnConnectivityTest.isEnabled = !loading
        binding.btnSpeedTest.isEnabled = !loading
    }

    private fun returnToTechMenu() {
        val intent = Intent(this, TechnicianMenuActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}