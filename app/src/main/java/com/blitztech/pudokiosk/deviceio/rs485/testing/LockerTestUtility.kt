package com.blitztech.pudokiosk.deviceio.rs485.testing

import android.util.Log
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol
import com.blitztech.pudokiosk.deviceio.rs485.config.STM32LockerConfig
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

/**
 * Comprehensive testing utility for STM32L412 Locker Controller
 *
 * Provides automated testing, validation, and diagnostics for the locker system.
 * Use this class to verify hardware functionality and protocol compliance.
 */
class LockerTestUtility(private val lockerController: LockerController) {

    companion object {
        private const val TAG = "LockerTestUtility"
    }

    /**
     * Test result data class
     */
    data class TestResult(
        val testName: String,
        val success: Boolean,
        val duration: Long,
        val details: String,
        val errors: List<String> = emptyList()
    )

    /**
     * System health report
     */
    data class HealthReport(
        val overallHealth: HealthStatus,
        val communicationHealth: HealthStatus,
        val lockResponseHealth: HealthStatus,
        val performanceHealth: HealthStatus,
        val testResults: List<TestResult>,
        val recommendations: List<String>
    )

    enum class HealthStatus {
        EXCELLENT, GOOD, WARNING, CRITICAL, UNKNOWN
    }

    private val testResults = mutableListOf<TestResult>()

    /**
     * Run basic connectivity test
     */
    suspend fun testBasicConnectivity(): TestResult {
        Log.i(TAG, "Starting basic connectivity test...")

        val errors = mutableListOf<String>()
        var details = ""

        val duration = measureTimeMillis {
            try {
                if (!lockerController.isConnected) {
                    errors.add("Locker controller not connected")
                    details = "Connection test failed - not connected"
                    return@measureTimeMillis
                }

                val commTest = lockerController.testCommunication()
                if (commTest) {
                    details = "Basic communication successful"
                } else {
                    errors.add("Communication test failed")
                    details = "Communication test returned false"
                }

            } catch (e: Exception) {
                errors.add("Exception: ${e.message}")
                details = "Test failed with exception"
                Log.e(TAG, "Basic connectivity test error", e)
            }
        }

        val result = TestResult(
            testName = "Basic Connectivity",
            success = errors.isEmpty(),
            duration = duration,
            details = details,
            errors = errors
        )

        testResults.add(result)
        return result
    }

    /**
     * Test individual lock operations
     */
    suspend fun testIndividualLocks(lockNumbers: List<Int> = STM32LockerConfig.Testing.QUICK_TEST_LOCKS): TestResult {
        Log.i(TAG, "Starting individual lock test for locks: $lockNumbers")

        val errors = mutableListOf<String>()
        var successCount = 0
        var totalOperations = 0

        val duration = measureTimeMillis {
            for (lockNumber in lockNumbers) {
                try {
                    // Test status check
                    totalOperations++
                    val statusResult = lockerController.checkLockStatus(lockNumber)
                    if (statusResult.success) {
                        successCount++
                    } else {
                        errors.add("Lock $lockNumber status check failed: ${statusResult.errorMessage}")
                    }

                    delay(100) // Small delay between operations

                    // Test unlock operation
                    totalOperations++
                    val unlockResult = lockerController.unlockLock(lockNumber)
                    if (unlockResult.success) {
                        successCount++
                    } else {
                        errors.add("Lock $lockNumber unlock failed: ${unlockResult.errorMessage}")
                    }

                    delay(200) // Allow lock to operate

                } catch (e: Exception) {
                    errors.add("Lock $lockNumber test exception: ${e.message}")
                    Log.e(TAG, "Individual lock test error for lock $lockNumber", e)
                }
            }
        }

        val details = "Tested ${lockNumbers.size} locks, $successCount/$totalOperations operations successful"

        val result = TestResult(
            testName = "Individual Lock Operations",
            success = successCount == totalOperations,
            duration = duration,
            details = details,
            errors = errors
        )

        testResults.add(result)
        return result
    }

    /**
     * Test performance and response times
     */
    suspend fun testPerformance(): TestResult {
        Log.i(TAG, "Starting performance test...")

        val errors = mutableListOf<String>()
        val performanceData = mutableListOf<String>()

        val duration = measureTimeMillis {
            try {
                // Test single lock response time
                repeat(5) { iteration ->
                    val responseTime = measureTimeMillis {
                        lockerController.checkLockStatus(1)
                    }
                    performanceData.add("Status check ${iteration + 1}: ${responseTime}ms")

                    if (responseTime > STM32LockerConfig.Testing.MAX_STATUS_RESPONSE_TIME_MS) {
                        errors.add("Slow response time: ${responseTime}ms (max: ${STM32LockerConfig.Testing.MAX_STATUS_RESPONSE_TIME_MS}ms)")
                    }

                    delay(50)
                }

                // Test unlock response time
                repeat(3) { iteration ->
                    val responseTime = measureTimeMillis {
                        lockerController.unlockLock(1)
                    }
                    performanceData.add("Unlock ${iteration + 1}: ${responseTime}ms")

                    if (responseTime > STM32LockerConfig.Testing.MAX_UNLOCK_RESPONSE_TIME_MS) {
                        errors.add("Slow unlock time: ${responseTime}ms (max: ${STM32LockerConfig.Testing.MAX_UNLOCK_RESPONSE_TIME_MS}ms)")
                    }

                    delay(100)
                }

            } catch (e: Exception) {
                errors.add("Performance test exception: ${e.message}")
                Log.e(TAG, "Performance test error", e)
            }
        }

        val details = "Performance measurements:\n${performanceData.joinToString("\n")}"

        val result = TestResult(
            testName = "Performance & Response Time",
            success = errors.isEmpty(),
            duration = duration,
            details = details,
            errors = errors
        )

        testResults.add(result)
        return result
    }

    /**
     * Test all lock statuses
     */
    suspend fun testAllLockStatuses(): TestResult {
        Log.i(TAG, "Starting all lock status test...")

        val errors = mutableListOf<String>()
        var statusCounts = mutableMapOf<WinnsenProtocol.LockStatus, Int>()

        val duration = measureTimeMillis {
            try {
                val results = lockerController.checkAllLockStatuses()

                for ((lockNumber, result) in results) {
                    if (!result.success) {
                        errors.add("Lock $lockNumber status check failed: ${result.errorMessage}")
                    } else {
                        statusCounts[result.status] = statusCounts.getOrDefault(result.status, 0) + 1
                    }
                }

            } catch (e: Exception) {
                errors.add("All status test exception: ${e.message}")
                Log.e(TAG, "All lock status test error", e)
            }
        }

        val details = buildString {
            append("Status summary: ")
            statusCounts.forEach { (status, count) ->
                append("${status.displayName}: $count, ")
            }
            if (isNotEmpty()) setLength(length - 2) // Remove last comma
        }

        val result = TestResult(
            testName = "All Lock Status Check",
            success = errors.isEmpty(),
            duration = duration,
            details = details,
            errors = errors
        )

        testResults.add(result)
        return result
    }

    /**
     * Test protocol compliance
     */
    suspend fun testProtocolCompliance(): TestResult {
        Log.i(TAG, "Starting protocol compliance test...")

        val errors = mutableListOf<String>()
        val details = mutableListOf<String>()

        val duration = measureTimeMillis {
            try {
                // Test valid lock numbers
                for (lockNumber in listOf(1, 8, 16)) {
                    val result = lockerController.checkLockStatus(lockNumber)
                    if (result.success) {
                        details.add("Lock $lockNumber: Protocol compliant")
                    } else {
                        errors.add("Lock $lockNumber protocol error: ${result.errorMessage}")
                    }
                }

                // Test response to invalid commands would go here
                // (requires low-level access to send malformed frames)

            } catch (e: Exception) {
                errors.add("Protocol test exception: ${e.message}")
                Log.e(TAG, "Protocol compliance test error", e)
            }
        }

        val result = TestResult(
            testName = "Protocol Compliance",
            success = errors.isEmpty(),
            duration = duration,
            details = details.joinToString("; "),
            errors = errors
        )

        testResults.add(result)
        return result
    }

    /**
     * Run comprehensive system health check
     */
    suspend fun runHealthCheck(): HealthReport {
        Log.i(TAG, "Starting comprehensive health check...")

        testResults.clear()

        // Run all tests
        val connectivityResult = testBasicConnectivity()
        delay(500)

        val individualResult = testIndividualLocks()
        delay(500)

        val performanceResult = testPerformance()
        delay(500)

        val statusResult = testAllLockStatuses()
        delay(500)

        val protocolResult = testProtocolCompliance()

        // Analyze results
        val communicationHealth = if (connectivityResult.success && protocolResult.success) {
            HealthStatus.EXCELLENT
        } else if (connectivityResult.success) {
            HealthStatus.GOOD
        } else {
            HealthStatus.CRITICAL
        }

        val lockResponseHealth = if (individualResult.success && statusResult.success) {
            HealthStatus.EXCELLENT
        } else if (individualResult.errors.size <= 2) {
            HealthStatus.WARNING
        } else {
            HealthStatus.CRITICAL
        }

        val performanceHealth = if (performanceResult.success) {
            HealthStatus.EXCELLENT
        } else if (performanceResult.errors.size <= 1) {
            HealthStatus.WARNING
        } else {
            HealthStatus.CRITICAL
        }

        val overallHealth = listOf(communicationHealth, lockResponseHealth, performanceHealth).minByOrNull {
            it.ordinal
        } ?: HealthStatus.UNKNOWN

        // Generate recommendations
        val recommendations = mutableListOf<String>()

        if (!connectivityResult.success) {
            recommendations.add("Check RS485 connection and wiring")
            recommendations.add("Verify STM32 power and firmware")
        }

        if (!performanceResult.success) {
            recommendations.add("Check for electrical interference")
            recommendations.add("Verify baud rate configuration")
        }

        if (individualResult.errors.isNotEmpty()) {
            recommendations.add("Inspect problematic locks for mechanical issues")
            recommendations.add("Check lock power supply")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("System is operating normally")
            recommendations.add("Continue regular monitoring")
        }

        return HealthReport(
            overallHealth = overallHealth,
            communicationHealth = communicationHealth,
            lockResponseHealth = lockResponseHealth,
            performanceHealth = performanceHealth,
            testResults = testResults.toList(),
            recommendations = recommendations
        )
    }

    /**
     * Generate test report
     */
    fun generateTestReport(healthReport: HealthReport): String {
        return buildString {
            appendLine("=== STM32L412 Locker Controller Test Report ===")
            appendLine("Generated: ${java.util.Date()}")
            appendLine()

            appendLine("Overall Health: ${healthReport.overallHealth}")
            appendLine("Communication: ${healthReport.communicationHealth}")
            appendLine("Lock Response: ${healthReport.lockResponseHealth}")
            appendLine("Performance: ${healthReport.performanceHealth}")
            appendLine()

            appendLine("=== Test Results ===")
            healthReport.testResults.forEach { result ->
                appendLine("${result.testName}: ${if (result.success) "PASS" else "FAIL"} (${result.duration}ms)")
                if (result.details.isNotEmpty()) {
                    appendLine("  Details: ${result.details}")
                }
                result.errors.forEach { error ->
                    appendLine("  Error: $error")
                }
                appendLine()
            }

            appendLine("=== Recommendations ===")
            healthReport.recommendations.forEach { recommendation ->
                appendLine("• $recommendation")
            }

            appendLine()
            appendLine("=== Configuration ===")
            append(STM32LockerConfig.getConfigurationSummary())
        }
    }
}