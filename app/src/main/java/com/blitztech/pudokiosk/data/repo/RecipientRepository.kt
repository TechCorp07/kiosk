package com.blitztech.pudokiosk.data.repo

import com.blitztech.pudokiosk.data.ServiceLocator
import com.blitztech.pudokiosk.data.net.ParcelItem
import com.blitztech.pudokiosk.data.net.ParcelsResponse
import kotlinx.coroutines.delay

class RecipientRepository(private val useStub: Boolean = true) {
    suspend fun listParcels(userId: String): List<ParcelItem> {
        if (useStub) {
            delay(300)
            return listOf(
                ParcelItem(parcelId="P1", lockerId="M12", size="M", tracking="TRK-DEMO1"),
                ParcelItem(parcelId="P2", lockerId="S07", size="S", tracking="TRK-DEMO2")
            )
        }
        return ServiceLocator.api.recipientParcels(userId).parcels
    }
}
