package com.blitztech.pudokiosk.data.repository

import com.blitztech.pudokiosk.data.net.CourierLoginResponse
import com.blitztech.pudokiosk.data.net.CourierParcel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CourierRepositoryTest {

    private val repo = CourierRepository(useStub = true)

    // ─────────────────────────────────────────────────────────────
    //  login() — stub mode
    // ─────────────────────────────────────────────────────────────
    @Test
    fun login_stub_returnsCourierResponse() = runTest {
        val response = repo.login("12345")
        assertNotNull(response)
        assertTrue(response.courierId.contains("345"))
        assertTrue(response.name.contains("12345"))
    }

    @Test
    fun login_stub_courierIdUsesLast3Digits() = runTest {
        val response = repo.login("987654")
        assertEquals("cr_654", response.courierId)
    }

    @Test
    fun login_stub_nameIncludesCode() = runTest {
        val response = repo.login("ABC")
        assertEquals("Courier ABC", response.name)
    }

    // ─────────────────────────────────────────────────────────────
    //  listToCollect() — stub mode
    // ─────────────────────────────────────────────────────────────
    @Test
    fun listToCollect_stub_returnsParcels() = runTest {
        val parcels = repo.listToCollect("any")
        assertEquals(2, parcels.size)
    }

    @Test
    fun listToCollect_stub_firstParcelHasId() = runTest {
        val parcels = repo.listToCollect("any")
        assertEquals("P1", parcels[0].parcelId)
        assertEquals("M12", parcels[0].lockerId)
        assertEquals("TRK-DEMO1", parcels[0].tracking)
        assertEquals("M", parcels[0].size)
    }

    @Test
    fun listToCollect_stub_secondParcelHasId() = runTest {
        val parcels = repo.listToCollect("any")
        assertEquals("P2", parcels[1].parcelId)
        assertEquals("S07", parcels[1].lockerId)
        assertEquals("TRK-DEMO2", parcels[1].tracking)
        assertEquals("S", parcels[1].size)
    }

    // ─────────────────────────────────────────────────────────────
    //  markCollected() — stub mode
    // ─────────────────────────────────────────────────────────────
    @Test
    fun markCollected_stub_doesNotThrow() = runTest {
        // Should complete without throwing
        repo.markCollected("P1")
    }

    // ─────────────────────────────────────────────────────────────
    //  Non-stub mode
    // ─────────────────────────────────────────────────────────────
    @Test(expected = NotImplementedError::class)
    fun login_nonStub_throwsNotImplemented() = runTest {
        val nonStubRepo = CourierRepository(useStub = false)
        nonStubRepo.login("12345")
    }

    @Test(expected = NotImplementedError::class)
    fun listToCollect_nonStub_throwsNotImplemented() = runTest {
        val nonStubRepo = CourierRepository(useStub = false)
        nonStubRepo.listToCollect("any")
    }

    @Test(expected = NotImplementedError::class)
    fun markCollected_nonStub_throwsNotImplemented() = runTest {
        val nonStubRepo = CourierRepository(useStub = false)
        nonStubRepo.markCollected("P1")
    }
}
