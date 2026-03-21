package com.blitztech.pudokiosk.deviceio.rs485

import org.junit.Assert.*
import org.junit.Test

class WinnsenProtocolTest {

    // ─────────────────────────────────────────────────────────────
    //  stationForLock()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun stationForLock_locks1to16_returnStation0() {
        for (lock in 1..16) {
            assertEquals("Lock $lock should be station 0",
                WinnsenProtocol.STATION_0, WinnsenProtocol.stationForLock(lock))
        }
    }

    @Test
    fun stationForLock_locks17to32_returnStation1() {
        for (lock in 17..32) {
            assertEquals("Lock $lock should be station 1",
                WinnsenProtocol.STATION_1, WinnsenProtocol.stationForLock(lock))
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  lockByteFor()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun lockByteFor_lock1_returns1() {
        assertEquals(1.toByte(), WinnsenProtocol.lockByteFor(1))
    }

    @Test
    fun lockByteFor_lock16_returns16() {
        assertEquals(16.toByte(), WinnsenProtocol.lockByteFor(16))
    }

    @Test
    fun lockByteFor_lock17_returns1() {
        // Lock 17 is the first slot on board 1
        assertEquals(1.toByte(), WinnsenProtocol.lockByteFor(17))
    }

    @Test
    fun lockByteFor_lock32_returns16() {
        assertEquals(16.toByte(), WinnsenProtocol.lockByteFor(32))
    }

    @Test
    fun lockByteFor_lock8_returns8() {
        assertEquals(8.toByte(), WinnsenProtocol.lockByteFor(8))
    }

    @Test
    fun lockByteFor_lock24_returns8() {
        // Lock 24 = slot 8 on board 1
        assertEquals(8.toByte(), WinnsenProtocol.lockByteFor(24))
    }

    // ─────────────────────────────────────────────────────────────
    //  isValidLockNumber()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun isValidLockNumber_validRange() {
        for (lock in 1..32) {
            assertTrue("Lock $lock should be valid", WinnsenProtocol.isValidLockNumber(lock))
        }
    }

    @Test
    fun isValidLockNumber_zero_invalid() {
        assertFalse(WinnsenProtocol.isValidLockNumber(0))
    }

    @Test
    fun isValidLockNumber_negative_invalid() {
        assertFalse(WinnsenProtocol.isValidLockNumber(-1))
    }

    @Test
    fun isValidLockNumber_33_invalid() {
        assertFalse(WinnsenProtocol.isValidLockNumber(33))
    }

    @Test
    fun isValidLockNumber_100_invalid() {
        assertFalse(WinnsenProtocol.isValidLockNumber(100))
    }

    // ─────────────────────────────────────────────────────────────
    //  createUnlockCommand()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun createUnlockCommand_validLock_returnsFrame() {
        val cmd = WinnsenProtocol.createUnlockCommand(1)
        assertNotNull(cmd)
        assertEquals(WinnsenProtocol.FUNC_UNLOCK, cmd!!.function)
        assertEquals(WinnsenProtocol.STATION_0, cmd.station)
        assertEquals(1.toByte(), cmd.lockNumber)
    }

    @Test
    fun createUnlockCommand_lock17_usesStation1() {
        val cmd = WinnsenProtocol.createUnlockCommand(17)
        assertNotNull(cmd)
        assertEquals(WinnsenProtocol.STATION_1, cmd!!.station)
        assertEquals(1.toByte(), cmd.lockNumber) // slot 1 on board 1
    }

    @Test
    fun createUnlockCommand_invalidLock_returnsNull() {
        assertNull(WinnsenProtocol.createUnlockCommand(0))
        assertNull(WinnsenProtocol.createUnlockCommand(33))
        assertNull(WinnsenProtocol.createUnlockCommand(-5))
    }

    // ─────────────────────────────────────────────────────────────
    //  createStatusCommand()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun createStatusCommand_validLock_returnsFrame() {
        val cmd = WinnsenProtocol.createStatusCommand(5)
        assertNotNull(cmd)
        assertEquals(WinnsenProtocol.FUNC_STATUS, cmd!!.function)
        assertEquals(WinnsenProtocol.STATION_0, cmd.station)
        assertEquals(5.toByte(), cmd.lockNumber)
    }

    @Test
    fun createStatusCommand_lock20_usesStation1Slot4() {
        val cmd = WinnsenProtocol.createStatusCommand(20)
        assertNotNull(cmd)
        assertEquals(WinnsenProtocol.STATION_1, cmd!!.station)
        assertEquals(4.toByte(), cmd.lockNumber)
    }

    @Test
    fun createStatusCommand_invalidLock_returnsNull() {
        assertNull(WinnsenProtocol.createStatusCommand(0))
        assertNull(WinnsenProtocol.createStatusCommand(99))
    }

    // ─────────────────────────────────────────────────────────────
    //  CommandFrame.toByteArray()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun commandFrame_toByteArray_correctStructure() {
        val frame = WinnsenProtocol.CommandFrame(
            function = WinnsenProtocol.FUNC_UNLOCK,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x05
        )
        val bytes = frame.toByteArray()
        assertEquals(6, bytes.size)
        assertEquals(0x90.toByte(), bytes[0]) // header
        assertEquals(0x06.toByte(), bytes[1]) // length
        assertEquals(WinnsenProtocol.FUNC_UNLOCK, bytes[2])
        assertEquals(0x01.toByte(), bytes[3]) // station (STATION_0 = 0x01 per protocol spec)
        assertEquals(0x05.toByte(), bytes[4]) // lock
        assertEquals(0x03.toByte(), bytes[5]) // frame end
    }

    @Test
    fun commandFrame_toHexString_formattedCorrectly() {
        val frame = WinnsenProtocol.CommandFrame(
            function = WinnsenProtocol.FUNC_STATUS,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x01
        )
        val hex = frame.toHexString()
        // Should be "90 06 12 00 01 03"
        assertTrue(hex.contains("90"))
        assertTrue(hex.contains("06"))
        assertTrue(hex.contains("03"))
    }

    // ─────────────────────────────────────────────────────────────
    //  ResponseFrame.fromByteArray()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun responseFrame_fromByteArray_validFrame() {
        val data = byteArrayOf(
            0x90.toByte(), 0x07, WinnsenProtocol.FUNC_UNLOCK_RESP,
            0x00, 0x01, WinnsenProtocol.STATUS_SUCCESS, 0x03
        )
        val frame = WinnsenProtocol.ResponseFrame.fromByteArray(data)
        assertNotNull(frame)
        assertEquals(0x90.toByte(), frame!!.header)
        assertEquals(0x07.toByte(), frame.length)
        assertEquals(WinnsenProtocol.FUNC_UNLOCK_RESP, frame.function)
        assertEquals(0x00.toByte(), frame.station)
        assertEquals(0x01.toByte(), frame.lockNumber)
        assertEquals(WinnsenProtocol.STATUS_SUCCESS, frame.status)
        assertEquals(0x03.toByte(), frame.frameEnd)
    }

    @Test
    fun responseFrame_fromByteArray_wrongSize_returnsNull() {
        val data = byteArrayOf(0x90.toByte(), 0x07, 0x85.toByte(), 0x00, 0x01)
        assertNull(WinnsenProtocol.ResponseFrame.fromByteArray(data))
    }

    @Test
    fun responseFrame_isValid_correctFrame_returnsTrue() {
        val frame = WinnsenProtocol.ResponseFrame(
            header = 0x90.toByte(),
            length = 0x07,
            function = WinnsenProtocol.FUNC_UNLOCK_RESP,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x01,
            status = WinnsenProtocol.STATUS_SUCCESS,
            frameEnd = 0x03
        )
        assertTrue(frame.isValid())
    }

    @Test
    fun responseFrame_isValid_badHeader_returnsFalse() {
        val frame = WinnsenProtocol.ResponseFrame(
            header = 0x00,
            length = 0x07,
            function = WinnsenProtocol.FUNC_UNLOCK_RESP,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x01,
            status = WinnsenProtocol.STATUS_SUCCESS,
            frameEnd = 0x03
        )
        assertFalse(frame.isValid())
    }

    @Test
    fun responseFrame_isValid_badLength_returnsFalse() {
        val frame = WinnsenProtocol.ResponseFrame(
            header = 0x90.toByte(),
            length = 0x06, // wrong, should be 0x07 for response
            function = WinnsenProtocol.FUNC_UNLOCK_RESP,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x01,
            status = WinnsenProtocol.STATUS_SUCCESS,
            frameEnd = 0x03
        )
        assertFalse(frame.isValid())
    }

    @Test
    fun responseFrame_isValid_badFrameEnd_returnsFalse() {
        val frame = WinnsenProtocol.ResponseFrame(
            header = 0x90.toByte(),
            length = 0x07,
            function = WinnsenProtocol.FUNC_UNLOCK_RESP,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x01,
            status = WinnsenProtocol.STATUS_SUCCESS,
            frameEnd = 0xFF.toByte()
        )
        assertFalse(frame.isValid())
    }

    @Test
    fun responseFrame_isValid_lockNumber0_returnsFalse() {
        val frame = WinnsenProtocol.ResponseFrame(
            header = 0x90.toByte(),
            length = 0x07,
            function = WinnsenProtocol.FUNC_UNLOCK_RESP,
            station = WinnsenProtocol.STATION_0,
            lockNumber = 0x00,
            status = WinnsenProtocol.STATUS_SUCCESS,
            frameEnd = 0x03
        )
        assertFalse(frame.isValid())
    }

    // ─────────────────────────────────────────────────────────────
    //  LockStatus.fromByte()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun lockStatus_fromByte_open() {
        assertEquals(WinnsenProtocol.LockStatus.OPEN,
            WinnsenProtocol.LockStatus.fromByte(WinnsenProtocol.STATUS_OPEN))
    }

    @Test
    fun lockStatus_fromByte_closed() {
        assertEquals(WinnsenProtocol.LockStatus.CLOSED,
            WinnsenProtocol.LockStatus.fromByte(WinnsenProtocol.STATUS_CLOSED))
    }

    @Test
    fun lockStatus_fromByte_unknown() {
        assertEquals(WinnsenProtocol.LockStatus.UNKNOWN,
            WinnsenProtocol.LockStatus.fromByte(0x42))
    }

    @Test
    fun lockStatus_displayNames() {
        assertEquals("Open", WinnsenProtocol.LockStatus.OPEN.displayName)
        assertEquals("Closed", WinnsenProtocol.LockStatus.CLOSED.displayName)
        assertEquals("Unknown", WinnsenProtocol.LockStatus.UNKNOWN.displayName)
    }

    // ─────────────────────────────────────────────────────────────
    //  CommandType.fromResponseCode()
    // ─────────────────────────────────────────────────────────────
    @Test
    fun commandType_fromResponseCode_unlock() {
        assertEquals(WinnsenProtocol.CommandType.UNLOCK,
            WinnsenProtocol.CommandType.fromResponseCode(WinnsenProtocol.FUNC_UNLOCK_RESP))
    }

    @Test
    fun commandType_fromResponseCode_status() {
        assertEquals(WinnsenProtocol.CommandType.STATUS,
            WinnsenProtocol.CommandType.fromResponseCode(WinnsenProtocol.FUNC_STATUS_RESP))
    }

    @Test
    fun commandType_fromResponseCode_unknown_returnsNull() {
        assertNull(WinnsenProtocol.CommandType.fromResponseCode(0x42))
    }

    @Test
    fun commandType_displayNames() {
        assertEquals("Unlock", WinnsenProtocol.CommandType.UNLOCK.displayName)
        assertEquals("Status Check", WinnsenProtocol.CommandType.STATUS.displayName)
    }

    // ─────────────────────────────────────────────────────────────
    //  LockOperationResult
    // ─────────────────────────────────────────────────────────────
    @Test
    fun lockOperationResult_defaults() {
        val result = WinnsenProtocol.LockOperationResult(
            success = true,
            lockNumber = 1,
            status = WinnsenProtocol.LockStatus.OPEN
        )
        assertNull(result.errorMessage)
        assertEquals(0L, result.responseTime)
    }

    // ─────────────────────────────────────────────────────────────
    //  Protocol constants
    // ─────────────────────────────────────────────────────────────
    @Test
    fun protocolConstants_areCorrect() {
        assertEquals(0x90.toByte(), WinnsenProtocol.FRAME_HEADER)
        assertEquals(0x03.toByte(), WinnsenProtocol.FRAME_END)
        assertEquals(0x06.toByte(), WinnsenProtocol.CMD_LENGTH)
        assertEquals(0x07.toByte(), WinnsenProtocol.RESP_LENGTH)
        assertEquals(1, WinnsenProtocol.MIN_LOCK)
        assertEquals(32, WinnsenProtocol.MAX_LOCK)
        assertEquals(16, WinnsenProtocol.LOCKS_PER_BOARD)
    }
}
