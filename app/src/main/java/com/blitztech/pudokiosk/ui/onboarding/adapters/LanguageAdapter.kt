package com.blitztech.pudokiosk.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val languages: List<Language>,
    private val onLanguageSelected: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private var selectedPosition = -1

    fun setSelectedLanguage(language: Language) {
        val position = languages.indexOf(language)
        if (position != -1) {
            selectedPosition = position
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(languages[position], position == selectedPosition)
    }

    override fun getItemCount() = languages.size

    inner class LanguageViewHolder(
        private val binding: ItemLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(language: Language, isSelected: Boolean) {
            binding.tvLanguageName.text = language.displayName
            binding.root.isSelected = isSelected
            binding.ivCheck.visibility = if (isSelected)
                android.view.View.VISIBLE else android.view.View.GONE

            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onLanguageSelected(language)
            }
        }
    }
}