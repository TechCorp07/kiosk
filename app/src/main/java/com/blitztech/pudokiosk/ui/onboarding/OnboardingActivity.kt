package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityOnboardingBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: Prefs
    private lateinit var onboardingAdapter: OnboardingPagerAdapter

    private val onboardingSlides = listOf(
        OnboardingSlide.SecureDeliverySlide,
        OnboardingSlide.FastDeliverySlide,
        OnboardingSlide.TrackingSlide
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ZimpudoApp.prefs
        setupViewPager()
        setupClickListeners()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingPagerAdapter(onboardingSlides)
        binding.viewPager.adapter = onboardingAdapter

        // Setup page indicator with 3 tabs
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ ->
            // Empty implementation for dot indicators
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigationButtons(position)
            }
        })

        updateNavigationButtons(0)
    }

    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            completeOnboarding()
        }

        binding.btnNext.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < onboardingSlides.size - 1) {
                binding.viewPager.currentItem = currentPosition + 1
            } else {
                completeOnboarding()
            }
        }
    }

    private fun updateNavigationButtons(position: Int) {
        if (position == onboardingSlides.size - 1) {
            binding.btnNext.text = getString(R.string.get_started)
        } else {
            binding.btnNext.text = getString(R.string.next)
        }
    }

    private fun completeOnboarding() {
        prefs.setOnboardingCompleted(true)

        val intent = Intent(this, UserTypeSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}