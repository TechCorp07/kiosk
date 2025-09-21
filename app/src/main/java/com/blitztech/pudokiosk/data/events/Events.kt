package com.blitztech.pudokiosk.data.events

sealed class EventType(val value: String) {
    data object USER_SIGNUP : EventType("USER_SIGNUP")
    data object PARCEL_CREATED : EventType("PARCEL_CREATED")
    data object PAYMENT_RESULT : EventType("PAYMENT_RESULT")
    data object LOCKER_OPEN_REQUEST : EventType("LOCKER_OPEN_REQUEST")
    data object LOCKER_OPEN_SUCCESS : EventType("LOCKER_OPEN_SUCCESS")
    data object LOCKER_OPEN_FAIL : EventType("LOCKER_OPEN_FAIL")
    data object LABEL_PRINTED : EventType("LABEL_PRINTED")
}
