package com.blitztech.pudokiosk.ui.onboarding

import com.blitztech.pudokiosk.R

/**
 * Onboarding slide data definitions
 */
sealed class OnboardingSlide(
    val titleKey: String,
    val subtitleKey: String,
    val imageRes: Int,
    val backgroundRes: Int
) {
    object WelcomeSlide : OnboardingSlide(
        titleKey = "onboarding_welcome_title",
        subtitleKey = "onboarding_welcome_subtitle",
        imageRes = R.drawable.onboarding_welcome,
        backgroundRes = R.drawable.bg_gradient_primary
    )

    object SecureDeliverySlide : OnboardingSlide(
        titleKey = "onboarding_secure_title",
        subtitleKey = "onboarding_secure_subtitle",
        imageRes = R.drawable.onboarding_secure,
        backgroundRes = R.drawable.bg_gradient_primary
    )

    object FastDeliverySlide : OnboardingSlide(
        titleKey = "onboarding_fast_title",
        subtitleKey = "onboarding_fast_subtitle",
        imageRes = R.drawable.onboarding_fast,
        backgroundRes = R.drawable.bg_gradient_primary
    )

    object TrackingSlide : OnboardingSlide(
        titleKey = "onboarding_tracking_title",
        subtitleKey = "onboarding_tracking_subtitle",
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