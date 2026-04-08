package com.blitztech.pudokiosk.data.api.dto.order

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PackageDimensions(
    @Json(name = "length") val length: Double,
    @Json(name = "width") val width: Double,
    @Json(name = "height") val height: Double
)

@JsonClass(generateAdapter = true)
data class PackageDetails(
    @Json(name = "packageSize") val packageSize: String, // "XS", "S", "M", "L", "XL"
    @Json(name = "packageDimensions") val packageDimensions: PackageDimensions,
    @Json(name = "contents") val contents: String,
    @Json(name = "packageClass") val packageClass: String // "STANDARD", "FRAGILE", "EXPRESS", "PERISHABLE"
)

@JsonClass(generateAdapter = true)
data class RecipientAddress(
    @Json(name = "streetAddress") val streetAddress: String,
    @Json(name = "suburb") val suburb: String,
    @Json(name = "city") val city: String,
    @Json(name = "houseNumber") val houseNumber: String,
    @Json(name = "country") val country: String = "Zimbabwe"
)

@JsonClass(generateAdapter = true)
data class Recipient(
    @Json(name = "name") val name: String,
    @Json(name = "surname") val surname: String,
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "nationalId") val nationalId: String,
    @Json(name = "address") val address: RecipientAddress
)

@JsonClass(generateAdapter = true)
data class SenderLocation(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lng") val lng: Double
)

@JsonClass(generateAdapter = true)
data class CreateOrderRequest(
    @Json(name = "packageDetails") val packageDetails: PackageDetails,
    @Json(name = "recipient") val recipient: Recipient,
    @Json(name = "senderLocation") val senderLocation: SenderLocation,
    @Json(name = "currency") val currency: String, // "USD" or "ZWG"
    @Json(name = "senderMode") val senderMode: String = "LOCKER_DROP",
    @Json(name = "receiverMode") val receiverMode: String = "LOCKER_PICKUP"
)

@JsonClass(generateAdapter = true)
data class LockerResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "name") val name: String,
    @Json(name = "id") val id: String,
    @Json(name = "longitude") val longitude: Double
)

@JsonClass(generateAdapter = true)
data class NearestLocker(
    @Json(name = "distance") val distance: Double,
    @Json(name = "lockerResponse") val lockerResponse: LockerResponse
)

@JsonClass(generateAdapter = true)
data class CreateOrderResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "distance") val distance: String,
    @Json(name = "currency") val currency: String, // "USD" or "ZWG"
    @Json(name = "price") val price: Double,
    @Json(name = "orderId") val orderId: String,
    @Json(name = "trackingNumber") val trackingNumber: String? = null,
    @Json(name = "nearestLockers") val nearestLockers: List<NearestLocker>
)

@JsonClass(generateAdapter = true)
data class PaymentRequest(
    @Json(name = "orderId") val orderId: String,
    @Json(name = "lockerId") val lockerId: String,
    @Json(name = "paymentMethod") val paymentMethod: String, // "ECOCASH", "INNBUCKS", "ONEMONEY", "TELECASH"
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "currency") val currency: String // "ZWG" or "USD"
)

/**
 * Matches backend GenericResponse { success, message, errors }.
 * The backend payment endpoint (POST /api/v1/payments) returns HTTP 202 with this shape.
 * Payment confirmation arrives asynchronously via Paynow webhook — the kiosk polls
 * order status to detect when the order moves to AWAITING_COURIER.
 */
@JsonClass(generateAdapter = true)
data class PaymentResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "errors") val errors: Map<String, String>? = null
)

/**
 * Page wrapper for payment search results from POST /api/v1/payments/or-search.
 * Only maps the fields we actually need for status polling.
 */
@JsonClass(generateAdapter = true)
data class PaymentSearchPage(
    @Json(name = "content") val content: List<PaymentSearchResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PaymentSearchResult(
    @Json(name = "paymentId") val paymentId: String? = null,
    @Json(name = "paymentStatus") val paymentStatus: String? = null,
    @Json(name = "orderId") val orderId: String? = null,
    @Json(name = "paymentReference") val paymentReference: String? = null
)

// Enum for package sizes with dimension thresholds
enum class PackageSize(
    val displayName: String,
    val maxLength: Double,
    val maxWidth: Double,
    val maxHeight: Double
) {
    XS("Extra Small", 0.15, 0.15, 0.10),
    S("Small", 0.30, 0.25, 0.20),
    M("Medium", 0.50, 0.40, 0.30),
    L("Large", 0.70, 0.60, 0.50),
    XL("Extra Large", 1.00, 0.80, 0.70);

    companion object {
        /**
         * Determine package size based on dimensions (in meters)
         */
        fun fromDimensions(length: Double, width: Double, height: Double): PackageSize {
            return when {
                length <= XS.maxLength && width <= XS.maxWidth && height <= XS.maxHeight -> XS
                length <= S.maxLength && width <= S.maxWidth && height <= S.maxHeight -> S
                length <= M.maxLength && width <= M.maxWidth && height <= M.maxHeight -> M
                length <= L.maxLength && width <= L.maxWidth && height <= L.maxHeight -> L
                else -> XL
            }
        }
    }
}

// Payment methods matching Paynow SDK's MobileMoneyMethod enum (v1.1.1).
// SDK supports: ECOCASH, TELECASH, ONEMONEY, INNBUCKS
// The kiosk sends the method name; Paynow gateway routes to the correct provider.
enum class PaymentMethod(val displayName: String, val apiValue: String) {
    ECOCASH("EcoCash", "ECOCASH"),
    INNBUCKS("InnBucks", "INNBUCKS"),
    ONEMONEY("OneMoney", "ONEMONEY"),
    TELECASH("TeleCash", "TELECASH");

    companion object {
        fun fromApiValue(value: String): PaymentMethod? {
            return values().find { it.apiValue == value }
        }
    }
}

// Enum for currencies – matches backend BankingDetailsDto currency enum
enum class Currency(val displayName: String, val code: String, val symbol: String) {
    USD("US Dollar", "USD", "$"),
    ZWG("Zimbabwe Gold", "ZWG", "ZiG");

    companion object {
        fun fromCode(code: String): Currency? {
            return values().find { it.code == code }
        }
    }
}