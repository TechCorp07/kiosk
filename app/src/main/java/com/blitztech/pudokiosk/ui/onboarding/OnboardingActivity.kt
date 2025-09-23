package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.blitztech.pudokiosk.databinding.ActivityOnboardingBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n
    private lateinit var onboardingAdapter: OnboardingPagerAdapter

    private val onboardingSlides = listOf(
        OnboardingSlide.WelcomeSlide,
        OnboardingSlide.SecureDeliverySlide,
        OnboardingSlide.FastDeliverySlide,
        OnboardingSlide.TrackingSlide
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        i18n = I18n(this)

        // Load current language
        val currentLocale = prefs.getLocale()
        i18n.load(currentLocale)

        setupViewPager()
        setupButtons()
        updateUI()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingPagerAdapter(onboardingSlides, i18n)
        binding.viewPager.adapter = onboardingAdapter

        // Setup page indicator
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ ->
            // Empty implementation for dots indicator
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateUI()
            }
        })
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            navigateToUserTypeSelection()
        }

        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < onboardingSlides.size - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                navigateToUserTypeSelection()
            }
        }

        binding.btnGetStarted.setOnClickListener {
            navigateToUserTypeSelection()
        }
    }

    private fun updateUI() {
        val currentPosition = binding.viewPager.currentItem
        val isLastSlide = currentPosition == onboardingSlides.size - 1

        // Update button visibility and text
        binding.btnSkip.text = i18n.t("skip", "Skip")
        binding.btnNext.text = i18n.t("next", "Next")
        binding.btnGetStarted.text = i18n.t("get_started", "Get Started")

        if (isLastSlide) {
            binding.btnSkip.visibility = android.view.View.GONE
            binding.btnNext.visibility = android.view.View.GONE
            binding.btnGetStarted.visibility = android.view.View.VISIBLE
        } else {
            binding.btnSkip.visibility = android.view.View.VISIBLE
            binding.btnNext.visibility = android.view.View.VISIBLE
            binding.btnGetStarted.visibility = android.view.View.GONE
        }
    }

    private fun navigateToUserTypeSelection() {
        val intent = Intent(this, UserTypeSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}