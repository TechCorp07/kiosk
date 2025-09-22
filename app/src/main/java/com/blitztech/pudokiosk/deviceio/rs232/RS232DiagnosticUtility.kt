package com.blitztech.pudokiosk.deviceio.rs232

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.*

/**
 * RS232 Diagnostic Utility for troubleshooting direct serial connections
 * Use this to diagnose connection issues with your Honeywell Xenon 1900
 */
class RS232DiagnosticUtility(private val ctx: Context) {

    companion object {
        private const val TAG = "RS232Diagnostic"
    }

    /**
     * Run comprehensive RS232 diagnostics
     */
    suspend fun runDiagnostics(): DiagnosticResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== RS232 DIAGNOSTIC UTILITY ===")

        val result = DiagnosticResult()

        // Step 1: Check device capabilities
        result.deviceInfo = checkDeviceCapabilities()

        // Step 2: Scan for available serial ports
        result.availablePorts = scanForSerialPorts()

        // Step 3: Test port accessibility
        result.portTests = testPortAccessibility(result.availablePorts)

        // Step 4: Check system permissions
        result.permissionInfo = checkPermissions()

        // Step 5: Test basic read/write operations
        result.ioTests = testBasicIO(result.availablePorts)

        // Generate recommendations
        result.recommendations = generateRecommendations(result)

        Log.i(TAG, "=== DIAGNOSTIC COMPLETE ===")
        return@withContext result
    }

    private fun checkDeviceCapabilities(): DeviceInfo {
        Log.d(TAG, "Checking device capabilities...")

        return DeviceInfo(
            androidVersion = android.os.Build.VERSION.RELEASE,
            apiLevel = android.os.Build.VERSION.SDK_INT,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            board = android.os.Build.BOARD,
            hardware = android.os.Build.HARDWARE,
            isRooted = checkRootAccess(),
            hasSerialSupport = checkSerialSupport()
        )
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor() == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSerialSupport(): Boolean {
        // Check if device has hardware serial port support
        val possiblePorts = listOf("/dev/ttyS0", "/dev/ttyS1", "/dev/ttyHS0", "/dev/ttyMSM0")
        return possiblePorts.any { File(it).exists() }
    }

    private fun scanForSerialPorts(): List<SerialPortInfo> {
        Log.d(TAG, "Scanning for serial ports...")

        val ports = mutableListOf<SerialPortInfo>()
        val devDir = File("/dev")

        if (devDir.exists() && devDir.isDirectory()) {
            val ttyFiles = devDir.listFiles { file ->
                file.name.startsWith("tty") && !file.name.contains("p")
            }

            ttyFiles?.forEach { file ->
                val info = SerialPortInfo(
                    path = file.absolutePath,
                    name = file.name,
                    exists = file.exists(),
                    readable = file.canRead(),
                    writable = file.canWrite(),
                    isCharacterDevice = file.absolutePath.let { path ->
                        try {
                            val stat = Runtime.getRuntime().exec("stat -c %F $path").inputStream
                                .bufferedReader().readText().trim()
                            stat.contains("character")
                        } catch (e: Exception) {
                            false
                        }
                    }
                )
                ports.add(info)

                Log.d(TAG, "Found: ${info.path} (R:${info.readable} W:${info.writable})")
            }
        }

        return ports.sortedBy { it.name }
    }

    private fun testPortAccessibility(ports: List<SerialPortInfo>): List<PortTestResult> {
        Log.d(TAG, "Testing port accessibility...")

        return ports.map { port ->
            PortTestResult(
                port = port,
                canOpen = testCanOpenPort(port.path),
                canRead = testCanReadPort(port.path),
                canWrite = testCanWritePort(port.path),
                errorMessage = null // Will be set if errors occur
            )
        }
    }

    private fun testCanOpenPort(portPath: String): Boolean {
        return try {
            val file = File(portPath)
            val input = FileInputStream(file)
            input.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Cannot open $portPath: ${e.message}")
            false
        }
    }

    private fun testCanReadPort(portPath: String): Boolean {
        return try {
            val file = File(portPath)
            val input = FileInputStream(file)
            val available = input.available() // Just test if we can call this
            input.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read $portPath: ${e.message}")
            false
        }
    }

    private fun testCanWritePort(portPath: String): Boolean {
        return try {
            val file = File(portPath)
            val output = FileOutputStream(file)
            output.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Cannot write $portPath: ${e.message}")
            false
        }
    }

    private fun checkPermissions(): PermissionInfo {
        Log.d(TAG, "Checking permissions...")

        return PermissionInfo(
            hasSerialPermission = ctx.checkSelfPermission("android.permission.ACCESS_SERIAL_PORT") == PackageManager.PERMISSION_GRANTED,
            hasSystemAppStatus = checkSystemAppStatus(),
            currentUserId = getCurrentUserId(),
            selinuxStatus = checkSelinuxStatus()
        )
    }

    private fun checkSystemAppStatus(): Boolean {
        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            (packageInfo.applicationInfo?.flags?.and(1)) != 0 // FLAG_SYSTEM
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentUserId(): String {
        return try {
            Runtime.getRuntime().exec("id").inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun checkSelinuxStatus(): String {
        return try {
            Runtime.getRuntime().exec("getenforce").inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "unknown"
        }
    }

    private suspend fun testBasicIO(ports: List<SerialPortInfo>): List<IOTestResult> {
        Log.d(TAG, "Testing basic I/O operations...")

        return ports.filter { it.readable && it.writable }.take(3).map { port ->
            testPortIO(port)
        }
    }

    private suspend fun testPortIO(port: SerialPortInfo): IOTestResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Testing I/O on ${port.path}...")

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            val file = File(port.path)
            inputStream = FileInputStream(file)
            outputStream = FileOutputStream(file)

            // Test write operation
            val testData = "HELLO\r\n".toByteArray()
            outputStream.write(testData)
            outputStream.flush()

            delay(100) // Wait for potential echo

            // Test read operation
            val available = inputStream.available()
            val readData = if (available > 0) {
                val buffer = ByteArray(available)
                inputStream.read(buffer)
                String(buffer)
            } else {
                ""
            }

            IOTestResult(
                port = port,
                writeSuccess = true,
                readSuccess = true,
                dataWritten = testData.size,
                dataRead = readData.length,
                echoReceived = readData.isNotEmpty()
            )

        } catch (e: Exception) {
            IOTestResult(
                port = port,
                writeSuccess = false,
                readSuccess = false,
                errorMessage = e.message
            )
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing test streams: ${e.message}")
            }
        }
    }

    private fun generateRecommendations(result: DiagnosticResult): List<String> {
        val recommendations = mutableListOf<String>()

        // Check if any ports are accessible
        val accessiblePorts = result.portTests.filter { it.canOpen && it.canRead && it.canWrite }

        if (accessiblePorts.isEmpty()) {
            recommendations.add("❌ No accessible serial ports found")

            if (!result.deviceInfo.isRooted) {
                recommendations.add("🔑 Root access required for direct serial port access")
                recommendations.add("📱 Consider using USB-to-Serial adapter instead")
            }

            if (result.permissionInfo.selinuxStatus == "Enforcing") {
                recommendations.add("🔒 SELinux is enforcing - may block serial access")
            }

            recommendations.add("🔄 Alternative: Use android-serialport-api library")
            recommendations.add("🔄 Alternative: Use Arduino/microcontroller bridge")
        } else {
            recommendations.add("✅ Found ${accessiblePorts.size} accessible serial port(s)")
            accessiblePorts.forEach { port ->
                recommendations.add("📌 Use port: ${port.port.path}")
            }
        }

        // Check device capabilities
        if (!result.deviceInfo.hasSerialSupport) {
            recommendations.add("⚠️ Device may not have hardware serial port support")
        }

        // Specific hardware recommendations
        when (result.deviceInfo.manufacturer.lowercase()) {
            "rockchip" -> recommendations.add("🪨 Rockchip devices often have /dev/ttyS* ports")
            "qualcomm" -> recommendations.add("📱 Try /dev/ttyMSM* ports for Qualcomm devices")
        }

        return recommendations
    }

    // Data classes for diagnostic results
    data class DiagnosticResult(
        var deviceInfo: DeviceInfo = DeviceInfo(),
        var availablePorts: List<SerialPortInfo> = emptyList(),
        var portTests: List<PortTestResult> = emptyList(),
        var permissionInfo: PermissionInfo = PermissionInfo(),
        var ioTests: List<IOTestResult> = emptyList(),
        var recommendations: List<String> = emptyList()
    )

    data class DeviceInfo(
        val androidVersion: String = "",
        val apiLevel: Int = 0,
        val manufacturer: String = "",
        val model: String = "",
        val board: String = "",
        val hardware: String = "",
        val isRooted: Boolean = false,
        val hasSerialSupport: Boolean = false
    )

    data class SerialPortInfo(
        val path: String,
        val name: String,
        val exists: Boolean,
        val readable: Boolean,
        val writable: Boolean,
        val isCharacterDevice: Boolean
    )

    data class PortTestResult(
        val port: SerialPortInfo,
        val canOpen: Boolean,
        val canRead: Boolean,
        val canWrite: Boolean,
        val errorMessage: String? = null
    )

    data class PermissionInfo(
        val hasSerialPermission: Boolean = false,
        val hasSystemAppStatus: Boolean = false,
        val currentUserId: String = "",
        val selinuxStatus: String = ""
    )

    data class IOTestResult(
        val port: SerialPortInfo,
        val writeSuccess: Boolean = false,
        val readSuccess: Boolean = false,
        val dataWritten: Int = 0,
        val dataRead: Int = 0,
        val echoReceived: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * Generate a human-readable diagnostic report
     */
    fun generateReport(result: DiagnosticResult): String {
        return buildString {
            appendLine("=== RS232 DIAGNOSTIC REPORT ===")
            appendLine()

            appendLine("📱 Device Information:")
            appendLine("  Android: ${result.deviceInfo.androidVersion} (API ${result.deviceInfo.apiLevel})")
            appendLine("  Device: ${result.deviceInfo.manufacturer} ${result.deviceInfo.model}")
            appendLine("  Hardware: ${result.deviceInfo.hardware}")
            appendLine("  Rooted: ${if (result.deviceInfo.isRooted) "Yes ✅" else "No ❌"}")
            appendLine("  Serial Support: ${if (result.deviceInfo.hasSerialSupport) "Yes ✅" else "Unknown ❓"}")
            appendLine()

            appendLine("🔌 Available Serial Ports (${result.availablePorts.size}):")
            result.availablePorts.forEach { port ->
                val status = when {
                    port.readable && port.writable -> "✅ R/W"
                    port.readable -> "📖 Read Only"
                    port.writable -> "✏️ Write Only"
                    else -> "❌ No Access"
                }
                appendLine("  ${port.path} - $status")
            }
            appendLine()

            appendLine("🔑 Permissions:")
            appendLine("  User ID: ${result.permissionInfo.currentUserId}")
            appendLine("  System App: ${if (result.permissionInfo.hasSystemAppStatus) "Yes ✅" else "No ❌"}")
            appendLine("  SELinux: ${result.permissionInfo.selinuxStatus}")
            appendLine()

            if (result.ioTests.isNotEmpty()) {
                appendLine("🔄 I/O Test Results:")
                result.ioTests.forEach { test ->
                    val status = if (test.writeSuccess && test.readSuccess) "✅ Pass" else "❌ Fail"
                    appendLine("  ${test.port.path} - $status")
                    if (test.errorMessage != null) {
                        appendLine("    Error: ${test.errorMessage}")
                    }
                }
                appendLine()
            }

            appendLine("💡 Recommendations:")
            result.recommendations.forEach { rec ->
                appendLine("  $rec")
            }

            appendLine()
            appendLine("=== END REPORT ===")
        }
    }
}