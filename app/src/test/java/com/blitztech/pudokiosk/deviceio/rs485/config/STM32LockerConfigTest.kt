package com.blitztech.pudokiosk.deviceio.rs485.config

import org.junit.Assert.*
import org.junit.Test

class STM32LockerConfigTest {

    // ─────────────────────────────────────────────────────────────
    //  isValidLockNumber()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun isValidLockNumber_validRange() {
        for (lock in 1..32) {
            assertTrue("Lock $lock should be valid", STM32LockerConfig.isValidLockNumber(lock))
        }
    }

    @Test
    fun isValidLockNumber_zero_invalid() {
        assertFalse(STM32LockerConfig.isValidLockNumber(0))
    }

    @Test
    fun isValidLockNumber_negative_invalid() {
        assertFalse(STM32LockerConfig.isValidLockNumber(-1))
    }

    @Test
    fun isValidLockNumber_33_invalid() {
        assertFalse(STM32LockerConfig.isValidLockNumber(33))
    }

    // ─────────────────────────────────────────────────────────────
    //  getLockPin()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun getLockPin_validLock_returnsPin() {
        assertNotNull(STM32LockerConfig.getLockPin(1))
        assertEquals("PB12", STM32LockerConfig.getLockPin(1))
    }

    @Test
    fun getLockPin_lock16_returnsPin() {
        assertNotNull(STM32LockerConfig.getLockPin(16))
        assertEquals("PB0", STM32LockerConfig.getLockPin(16))
    }

    @Test
    fun getLockPin_invalidLock_returnsNull() {
        assertNull(STM32LockerConfig.getLockPin(0))
        assertNull(STM32LockerConfig.getLockPin(33))
    }

    // If lock > 16, the PinMapping only maps 1-16, so 17+ should return null
    @Test
    fun getLockPin_lock17plus_returnsNull() {
        assertNull(STM32LockerConfig.getLockPin(17))
    }

    // ─────────────────────────────────────────────────────────────
    //  getStatusPin()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun getStatusPin_validLock_returnsPin() {
        assertNotNull(STM32LockerConfig.getStatusPin(1))
        assertEquals("PA15", STM32LockerConfig.getStatusPin(1))
    }

    @Test
    fun getStatusPin_lock16_returnsPin() {
        assertEquals("PC0", STM32LockerConfig.getStatusPin(16))
    }

    @Test
    fun getStatusPin_invalidLock_returnsNull() {
        assertNull(STM32LockerConfig.getStatusPin(0))
        assertNull(STM32LockerConfig.getStatusPin(-1))
    }

    // ─────────────────────────────────────────────────────────────
    //  getConfigurationSummary()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun getConfigurationSummary_containsKeyInfo() {
        val summary = STM32LockerConfig.getConfigurationSummary()
        assertTrue(summary.contains("STM32L412"))
        assertTrue(summary.contains("32")) // total locks
        assertTrue(summary.contains("9600")) // baud rate
    }

    @Test
    fun getConfigurationSummary_notEmpty() {
        assertTrue(STM32LockerConfig.getConfigurationSummary().isNotBlank())
    }

    // ─────────────────────────────────────────────────────────────
    //  Constants
    // ─────────────────────────────────────────────────────────────
    @Test
    fun constants_areCorrect() {
        assertEquals(32, STM32LockerConfig.TOTAL_LOCKS)
        assertEquals(16, STM32LockerConfig.LOCKS_PER_BOARD)
        assertEquals(2, STM32LockerConfig.NUM_BOARDS)
        assertEquals(9600, STM32LockerConfig.BAUD_RATE)
        assertEquals(200L, STM32LockerConfig.LOCK_PULSE_TIME_MS)
    }

    @Test
    fun testingConfig_quickTestLocks_coversBoundaries() {
        val locks = STM32LockerConfig.Testing.QUICK_TEST_LOCKS
        assertTrue(locks.contains(1))   // first
        assertTrue(locks.contains(32))  // last
        assertTrue(locks.contains(16))  // end of board 0
        assertTrue(locks.contains(17))  // start of board 1
    }

    @Test
    fun performanceTests_notEmpty() {
        assertTrue(STM32LockerConfig.PERFORMANCE_TESTS.isNotEmpty())
    }

    @Test
    fun pinMapping_has16LockPins() {
        assertEquals(16, STM32LockerConfig.PinMapping.LOCK_PINS.size)
    }

    @Test
    fun pinMapping_has16StatusPins() {
        assertEquals(16, STM32LockerConfig.PinMapping.STATUS_PINS.size)
    }

    @Test
    fun pinMapping_rs485Pins() {
        assertEquals("PA2", STM32LockerConfig.PinMapping.RS485_TX)
        assertEquals("PA3", STM32LockerConfig.PinMapping.RS485_RX)
        assertEquals("PA1", STM32LockerConfig.PinMapping.RS485_EN)
    }
}
