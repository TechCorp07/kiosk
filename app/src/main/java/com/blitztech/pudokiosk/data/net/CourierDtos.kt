package com.blitztech.pudokiosk.data.net

data class CourierLoginRequest(val codeOrPin: String)
data class CourierLoginResponse(val courierId: String, val name: String)
data class CourierParcel(val parcelId: String, val lockerId: String, val tracking: String, val size: String)
data class CourierParcelList(val items: List<CourierParcel>)
