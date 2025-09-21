package com.blitztech.pudokiosk.data.repo

import com.blitztech.pudokiosk.data.net.CourierLoginResponse
import com.blitztech.pudokiosk.data.net.CourierParcel
import kotlinx.coroutines.delay

class CourierRepository(private val useStub: Boolean = true) {

    suspend fun login(codeOrPin: String): CourierLoginResponse {
        if (useStub) {
            delay(200)
            return CourierLoginResponse(
                courierId = "cr_${codeOrPin.takeLast(3)}",
                name = "Courier $codeOrPin"
            )
        } else {
            // Replace with Retrofit call, e.g.:
            // return ServiceLocator.api.courierLogin(CourierLoginRequest(codeOrPin))
            throw NotImplementedError("CourierRepository.login(): implement Retrofit call")
        }
    }

    suspend fun listToCollect(courierId: String): List<CourierParcel> {
        if (useStub) {
            delay(200)
            return listOf(
                CourierParcel(parcelId = "P1", lockerId = "M12", tracking = "TRK-DEMO1", size = "M"),
                CourierParcel(parcelId = "P2", lockerId = "S07", tracking = "TRK-DEMO2", size = "S")
            )
        } else {
            // Replace with Retrofit call, e.g.:
            // return ServiceLocator.api.courierParcels(courierId).items
            throw NotImplementedError("CourierRepository.listToCollect(): implement Retrofit call")
        }
    }

    suspend fun markCollected(parcelId: String) {
        if (useStub) {
            delay(100)
            return
        } else {
            // Replace with Retrofit call, e.g.:
            // ServiceLocator.api.courierMarkCollected(parcelId)
            throw NotImplementedError("CourierRepository.markCollected(): implement Retrofit call")
        }
    }
}
