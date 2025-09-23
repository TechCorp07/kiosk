package com.blitztech.pudokiosk.ui.onboarding

import com.blitztech.pudokiosk.R

sealed class OnboardingSlide(
    val titleKey: String,
    val subtitleKey: String,
    val imageRes: Int,
    val backgroundRes: Int
) {
    object WelcomeSlide : OnboardingSlide(
        "onboarding_welcome_title",
        "onboarding_welcome_subtitle",
        R.drawable.onboarding_welcome,
        R.drawable.bg_gradient_primary
    )

    object SecureDeliverySlide : OnboardingSlide(
        "onboarding_secure_title",
        "onboarding_secure_subtitle",
        R.drawable.onboarding_secure,
        R.drawable.bg_gradient_primary
    )

    object FastDeliverySlide : OnboardingSlide(
        "onboarding_welcome_title", // Using welcome title for fast delivery
        "onboarding_welcome_subtitle",
        R.drawable.onboarding_fast,
        R.drawable.bg_gradient_primary
    )

    object TrackingSlide : OnboardingSlide(
        "onboarding_tracking_title",
        "onboarding_tracking_subtitle",
        R.drawable.onboarding_tracking,
        R.drawable.bg_gradient_primary
    )
}