package com.blitztech.pudokiosk.ui.sendpackage

import android.os.Parcelable
import com.blitztech.pudokiosk.data.api.dto.order.*
import kotlinx.parcelize.Parcelize

/**
 * Data class to hold send package flow state across fragments
 */
@Parcelize
data class SendPackageData(
    // Recipient details
    var recipientName: String = "",
    var recipientSurname: String = "",
    var recipientMobile: String = "",
    var recipientNationalId: String = "",
    var recipientHouseNumber: String = "",
    var recipientStreet: String = "",
    var recipientSuburbId: String = "",
    var recipientSuburbName: String = "",
    var recipientCityId: String = "",
    var recipientCityName: String = "",

    // Package details
    var packageLength: Double = 0.0,
    var packageWidth: Double = 0.0,
    var packageHeight: Double = 0.0,
    var packageContents: String = "",
    var packageSize: PackageSize? = null,
    var currency: Currency? = null,
    var packageClass: String = "",       // "STANDARD", "FRAGILE", "EXPRESS", "PERISHABLE"
    var packageClassName: String = "",

    // Sender location
    var senderLatitude: Double = 0.0,
    var senderLongitude: Double = 0.0,

    // Order response
    var orderId: String = "",
    var orderPrice: Double = 0.0,
    var orderDistance: String = "",
    var lockerId: String = "",

    // Payment details
    var paymentMethod: PaymentMethod? = null,
    var paymentMobileNumber: String = "",

    // Post-payment
    var transactionId: String = "",
    var assignedLockNumber: Int = 0
) : Parcelable {

    /**
     * Check if recipient details are complete
     */
    fun isRecipientDetailsComplete(): Boolean {
        return recipientName.isNotBlank() &&
                recipientSurname.isNotBlank() &&
                recipientMobile.isNotBlank() &&
                recipientNationalId.isNotBlank() &&
                recipientHouseNumber.isNotBlank() &&
                recipientStreet.isNotBlank() &&
                recipientSuburbId.isNotBlank() &&
                recipientCityId.isNotBlank()
    }

    /**
     * Check if package details are complete
     */
    fun isPackageDetailsComplete(): Boolean {
        return packageLength > 0.0 &&
                packageWidth > 0.0 &&
                packageHeight > 0.0 &&
                packageContents.isNotBlank() &&
                packageSize != null &&
                currency != null &&
                packageClass.isNotBlank()
    }

    /**
     * Check if payment details are complete
     */
    fun isPaymentDetailsComplete(): Boolean {
        return orderId.isNotBlank() &&
                paymentMethod != null &&
                paymentMobileNumber.isNotBlank()
    }

    /**
     * Build Recipient object for API
     */
    fun buildRecipient(): Recipient {
        return Recipient(
            name = recipientName,
            surname = recipientSurname,
            mobileNumber = recipientMobile,
            nationalId = recipientNationalId,
            address = RecipientAddress(
                streetAddress = recipientStreet,
                suburb = recipientSuburbName,
                city = recipientCityName,
                houseNumber = recipientHouseNumber,
                country = "Zimbabwe"
            )
        )
    }

    /**
     * Build PackageDetails object for API
     */
    fun buildPackageDetails(): PackageDetails {
        return PackageDetails(
            packageSize = packageSize!!.name,
            packageDimensions = PackageDimensions(
                length = packageLength,
                width = packageWidth,
                height = packageHeight
            ),
            contents = packageContents,
            packageClass = packageClass
        )
    }

    /**
     * Build SenderLocation object for API
     */
    fun buildSenderLocation(): SenderLocation {
        return SenderLocation(
            lat = senderLatitude,
            lng = senderLongitude
        )
    }
}