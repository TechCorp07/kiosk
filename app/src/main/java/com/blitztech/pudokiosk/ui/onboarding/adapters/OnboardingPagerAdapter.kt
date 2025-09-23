package com.blitztech.pudokiosk.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.databinding.FragmentOnboardingSlideBinding
import com.blitztech.pudokiosk.i18n.I18n

class OnboardingPagerAdapter(
    private val slides: List<OnboardingSlide>,
    private val i18n: I18n
) : RecyclerView.Adapter<OnboardingPagerAdapter.SlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = FragmentOnboardingSlideBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount() = slides.size

    inner class SlideViewHolder(
        private val binding: FragmentOnboardingSlideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(slide: OnboardingSlide) {
            binding.root.setBackgroundResource(slide.backgroundRes)
            binding.ivSlideImage.setImageResource(slide.imageRes)
            binding.tvSlideTitle.text = i18n.t(slide.titleKey, slide.titleKey)
            binding.tvSlideSubtitle.text = i18n.t(slide.subtitleKey, slide.subtitleKey)
        }
    }
}