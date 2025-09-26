package com.blitztech.pudokiosk.ui.auth

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SignUpFormData(
    val name: String,
    val surname: String,
    val email: String,
    val mobileNumber: String,
    val nationalId: String,
    val houseNumber: String,
    val street: String,
    val suburb: String,
    val city: String,
) : Parcelable