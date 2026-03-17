package com.blitztech.pudokiosk.deviceio.camera

/**
 * Identifies the transaction trigger point for a security photo capture.
 *
 * Each value maps to a specific moment in a kiosk workflow where
 * a single photo of the user is taken for accountability tracking.
 */
enum class PhotoReason(val displayName: String) {
    /** Customer is about to deposit a parcel — captured before locker opens. */
    CLIENT_DEPOSIT("Client Deposit"),

    /** Recipient entered a valid collection code — captured before locker opens. */
    CLIENT_COLLECTION("Client Collection"),

    /** Courier successfully authenticated — captured after login. */
    COURIER_LOGIN("Courier Login"),

    /** Courier is collecting a parcel — captured before locker opens. */
    COURIER_COLLECT("Courier Collect"),

    /** Courier is delivering a parcel — captured before locker opens. */
    COURIER_DELIVER("Courier Deliver")
}
