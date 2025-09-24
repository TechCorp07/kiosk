package com.blitztech.pudokiosk.ui.onboarding

import com.blitztech.pudokiosk.R

/**
 * Onboarding slide data definitions
 */
sealed class OnboardingSlide(
    val imageRes: Int,
    val backgroundRes: Int
) {
    object WelcomeSlide : OnboardingSlide(
        imageRes = R.drawable.onboarding_welcome,
        backgroundRes = R.drawable.bg_gradient_primary
    )

    object SecureDeliverySlide : OnboardingSlide(
        imageRes = R.drawable.onboarding_secure,
        backgroundRes = R.drawable.bg_gradient_primary
    )

    object FastDeliverySlide : OnboardingSlide(
        imageRes = R.drawable.onboarding_fast,
        backgroundRes = R.drawable.bg_gradient_primary
    )

    object TrackingSlide : OnboardingSlide(
        imageRes = R.drawable.onboarding_tracking,
        backgroundRes = R.drawable.bg_gradient_primary
    )
}

/**
 * Language selection data
 */
data class Language(
    val code: String,
    val displayName: String,
    val nativeName: String
)

/**
 * User type selection
 */
enum class UserType {
    CUSTOMER,
    COURIER
}