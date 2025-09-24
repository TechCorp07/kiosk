package com.blitztech.pudokiosk.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.databinding.FragmentOnboardingSlideBinding

class OnboardingPagerAdapter(
    private val slides: List<OnboardingSlide>,
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
        }
    }
}