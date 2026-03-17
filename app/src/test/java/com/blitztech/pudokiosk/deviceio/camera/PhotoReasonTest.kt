package com.blitztech.pudokiosk.deviceio.camera

import org.junit.Assert.*
import org.junit.Test

class PhotoReasonTest {

    @Test
    fun photoReason_hasCorrectCount() {
        assertEquals(5, PhotoReason.values().size)
    }

    @Test
    fun clientDeposit_displayName() {
        assertEquals("Client Deposit", PhotoReason.CLIENT_DEPOSIT.displayName)
    }

    @Test
    fun clientCollection_displayName() {
        assertEquals("Client Collection", PhotoReason.CLIENT_COLLECTION.displayName)
    }

    @Test
    fun courierLogin_displayName() {
        assertEquals("Courier Login", PhotoReason.COURIER_LOGIN.displayName)
    }

    @Test
    fun courierCollect_displayName() {
        assertEquals("Courier Collect", PhotoReason.COURIER_COLLECT.displayName)
    }

    @Test
    fun courierDeliver_displayName() {
        assertEquals("Courier Deliver", PhotoReason.COURIER_DELIVER.displayName)
    }

    @Test
    fun photoReason_names_matchEnumValues() {
        assertEquals("CLIENT_DEPOSIT", PhotoReason.CLIENT_DEPOSIT.name)
        assertEquals("CLIENT_COLLECTION", PhotoReason.CLIENT_COLLECTION.name)
        assertEquals("COURIER_LOGIN", PhotoReason.COURIER_LOGIN.name)
        assertEquals("COURIER_COLLECT", PhotoReason.COURIER_COLLECT.name)
        assertEquals("COURIER_DELIVER", PhotoReason.COURIER_DELIVER.name)
    }

    @Test
    fun photoReason_valueOf_works() {
        assertEquals(PhotoReason.CLIENT_DEPOSIT, PhotoReason.valueOf("CLIENT_DEPOSIT"))
        assertEquals(PhotoReason.COURIER_LOGIN, PhotoReason.valueOf("COURIER_LOGIN"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun photoReason_valueOf_invalid_throws() {
        PhotoReason.valueOf("INVALID_REASON")
    }
}
