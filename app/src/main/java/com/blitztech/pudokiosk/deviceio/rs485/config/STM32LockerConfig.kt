package com.blitztech.pudokiosk.deviceio.rs485.config

/**
 * STM32L412 Locker Controller Configuration
 *
 * This file contains all configuration constants that match the STM32L412 firmware.
 * Keep this synchronized with main.c definitions.
 *
 * Hardware Configuration:
 * - MCU: STM32L412
 * - Locks: 16 solenoid locks (LOCK1-16)
 * - Status: 16 status sensors (STATE1-16)
 * - Communication: RS485 via MAX3485 (PA2=TX, PA3=RX, PA1=EN)
 * - Protocol: Winnsen Smart Locker 9600 8N1
 */
object STM32LockerConfig {

    // === HARDWARE SPECIFICATIONS ===
    const val MCU_TYPE = "STM32L412"
    const val BOARD_VERSION = "v1.0"
    const val FIRMWARE_VERSION = "Production"

    // === LOCK CONFIGURATION ===
    const val TOTAL_LOCKS = 16
    const val LOCK_PULSE_TIME_MS = 200L
    const val LOCK_TYPE = "Solenoid (Normally Closed)"

    // === COMMUNICATION CONFIGURATION ===
    const val BAUD_RATE = 9600
    const val DATA_BITS = 8
    const val STOP_BITS = 1
    const val PARITY = "None"
    const val STATION_NUMBER = 0 // Single board configuration

    // === PROTOCOL TIMEOUTS (matching STM32 values) ===
    const val FRAME_TIMEOUT_MS = 1000L
    const val UART_ERROR_RETRY_COUNT = 3
    const val HEARTBEAT_INTERVAL_MS = 5000L
    const val RESPONSE_TIMEOUT_MS = 2000L

    // === PIN MAPPING (for reference/documentation) ===
    object PinMapping {
        // Lock Output Pins
        val LOCK_PINS = mapOf(
            1 to "PB12", 2 to "PB13", 3 to "PB14", 4 to "PB15",
            5 to "PC6", 6 to "PC7", 7 to "PC8", 8 to "PC9",
            9 to "PA8", 10 to "PA11", 11 to "PA12", 12 to "PB11",
            13 to "PB10", 14 to "PB2", 15 to "PB1", 16 to "PB0"
        )

        // Status Input Pins
        val STATUS_PINS = mapOf(
            1 to "PA15", 2 to "PC10", 3 to "PC11", 4 to "PC12",
            5 to "PD2", 6 to "PB3", 7 to "PB4", 8 to "PB5",
            9 to "PC5", 10 to "PC4", 11 to "PA7", 12 to "PA6",
            13 to "PC3", 14 to "PC2", 15 to "PC1", 16 to "PC0"
        )

        // RS485 Communication Pins
        const val RS485_TX = "PA2"  // USART2_TX
        const val RS485_RX = "PA3"  // USART2_RX
        const val RS485_EN = "PA1"  // Direction control for MAX3485
    }

    // === SYSTEM MONITORING ===
    object Monitoring {
        const val DEBUG_COUNTER_ENABLED = true
        const val HEARTBEAT_LED_PIN = "PB12" // Using Lock 1 pin as status LED
        const val SYSTEM_READY_FLASH_COUNT = 3
        const val SYSTEM_READY_FLASH_DELAY_MS = 200L
    }

    // === ERROR HANDLING ===
    object ErrorHandling {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 500L
        const val WATCHDOG_TIMEOUT_MS = 5000L
        const val AUTO_RECOVERY_ENABLED = true
    }

    // === TESTING CONFIGURATION ===
    object Testing {
        const val DEFAULT_TEST_LOCK = 1
        const val EMERGENCY_UNLOCK_CONFIRMATION_REQUIRED = true
        const val STRESS_TEST_ITERATIONS = 100
        const val STRESS_TEST_DELAY_MS = 50L

        // Test sequences for comprehensive testing
        val QUICK_TEST_LOCKS = listOf(1, 8, 16) // Test corners and middle
        val FULL_TEST_LOCKS = (1..16).toList()

        // Expected response times (for performance testing)
        const val MAX_UNLOCK_RESPONSE_TIME_MS = 500L
        const val MAX_STATUS_RESPONSE_TIME_MS = 200L
        const val MAX_BATCH_OPERATION_TIME_MS = 10000L
    }

    // === MAINTENANCE FEATURES ===
    object Maintenance {
        const val HEALTH_CHECK_INTERVAL_MS = 30000L // 30 seconds
        const val LOCK_WEAR_COUNTER_ENABLED = true
        const val AUTOMATIC_DIAGNOSTICS_ENABLED = true

        // Health check thresholds
        const val MAX_FAILED_OPERATIONS_THRESHOLD = 5
        const val COMMUNICATION_ERROR_THRESHOLD = 3
        const val RESPONSE_TIME_WARNING_MS = 1000L
    }

    /**
     * Generate a summary of the current configuration
     */
    fun getConfigurationSummary(): String {
        return buildString {
            appendLine("STM32L412 Locker Controller Configuration")
            appendLine("========================================")
            appendLine("MCU: $MCU_TYPE")
            appendLine("Board Version: $BOARD_VERSION")
            appendLine("Firmware: $FIRMWARE_VERSION")
            appendLine("Total Locks: $TOTAL_LOCKS")
            appendLine("Communication: RS485 @ ${BAUD_RATE} baud")
            appendLine("Station: $STATION_NUMBER")
            appendLine("Lock Pulse Time: ${LOCK_PULSE_TIME_MS}ms")
            appendLine("Response Timeout: ${RESPONSE_TIMEOUT_MS}ms")
            appendLine("Auto Recovery: ${ErrorHandling.AUTO_RECOVERY_ENABLED}")
            appendLine("Health Monitoring: ${Maintenance.AUTOMATIC_DIAGNOSTICS_ENABLED}")
        }
    }

    /**
     * Validate lock number against hardware configuration
     */
    fun isValidLockNumber(lockNumber: Int): Boolean {
        return lockNumber in 1..TOTAL_LOCKS
    }

    /**
     * Get pin assignment for a specific lock
     */
    fun getLockPin(lockNumber: Int): String? {
        return if (isValidLockNumber(lockNumber)) {
            PinMapping.LOCK_PINS[lockNumber]
        } else null
    }

    /**
     * Get status pin assignment for a specific lock
     */
    fun getStatusPin(lockNumber: Int): String? {
        return if (isValidLockNumber(lockNumber)) {
            PinMapping.STATUS_PINS[lockNumber]
        } else null
    }

    /**
     * Performance testing configuration
     */
    data class PerformanceTest(
        val testName: String,
        val lockNumbers: List<Int>,
        val iterations: Int,
        val expectedMaxTime: Long,
        val description: String
    )

    val PERFORMANCE_TESTS = listOf(
        PerformanceTest(
            testName = "Quick Response Test",
            lockNumbers = listOf(1),
            iterations = 10,
            expectedMaxTime = Testing.MAX_STATUS_RESPONSE_TIME_MS,
            description = "Test basic response time for single lock status"
        ),
        PerformanceTest(
            testName = "Unlock Performance Test",
            lockNumbers = Testing.QUICK_TEST_LOCKS,
            iterations = 5,
            expectedMaxTime = Testing.MAX_UNLOCK_RESPONSE_TIME_MS,
            description = "Test unlock response time for multiple locks"
        ),
        PerformanceTest(
            testName = "Full System Test",
            lockNumbers = Testing.FULL_TEST_LOCKS,
            iterations = 1,
            expectedMaxTime = Testing.MAX_BATCH_OPERATION_TIME_MS,
            description = "Test all locks status check performance"
        )
    )
}