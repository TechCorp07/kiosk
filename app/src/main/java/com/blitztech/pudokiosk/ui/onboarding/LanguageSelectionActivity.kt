package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityLanguageSelectionBinding
import com.blitztech.pudokiosk.databinding.ItemLanguageBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import java.util.*

class LanguageSelectionActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityLanguageSelectionBinding
    private lateinit var prefs: Prefs
    private lateinit var languageAdapter: LanguageAdapter

    private val languages = listOf(
        Language("en", "English", "English"),
        Language("sn", "Shona", "Shona"),
        Language("nd", "isiNdebele", "Ndebele")
    )

    private var selectedLanguage: Language? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDependencies()
        setupViews()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupDependencies() {
        prefs = ZimpudoApp.prefs
    }

    private fun setupViews() {
        // ✅ Use standard Android strings
        binding.tvTitle.text = getString(R.string.select_language)
        binding.tvSubtitle.text = getString(R.string.language_subtitle)
        binding.btnContinue.text = getString(R.string.continue_button)
        binding.btnContinue.isEnabled = false
    }

    private fun setupRecyclerView() {
        languageAdapter = LanguageAdapter(languages) { language ->
            selectedLanguage = language
            binding.btnContinue.isEnabled = true

            // Apply the selected language immediately for preview
            applyLanguage(language.code)
        }

        binding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@LanguageSelectionActivity)
            adapter = languageAdapter
        }

        // Select current locale by default
        val currentLocale = prefs.getLocale()
        selectedLanguage = languages.firstOrNull { it.code == currentLocale }
            ?: languages.first { it.code == "en" }
        languageAdapter.setSelectedLanguage(selectedLanguage!!)
        binding.btnContinue.isEnabled = true
    }

    private fun setupClickListeners() {
        binding.btnContinue.setOnClickListener {
            selectedLanguage?.let { language ->
                // Save the selected language
                prefs.setLocale(language.code)

                // Apply language globally
                applyLanguage(language.code)

                // Navigate to next screen
                navigateToOnboarding()
            }
        }
    }

    private fun applyLanguage(localeCode: String) {
        val locale = when (localeCode) {
            "sn" -> Locale("sn", "ZW") // Shona
            "nd" -> Locale("nd", "ZW") // Ndebele
            else -> Locale("en", "ZW") // English default
        }

        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Update UI text immediately
        updateUIText()
    }

    private fun updateUIText() {
        // ✅ Refresh UI with new language
        binding.tvTitle.text = getString(R.string.select_language)
        binding.tvSubtitle.text = getString(R.string.language_subtitle)
        binding.btnContinue.text = getString(R.string.continue_button)
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Keep the existing LanguageAdapter class unchanged
    inner class LanguageAdapter(
        private val languages: List<Language>,
        private val onLanguageSelected: (Language) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

        private var selectedPosition = -1

        fun setSelectedLanguage(language: Language) {
            selectedPosition = languages.indexOf(language)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            val binding = ItemLanguageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return LanguageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            holder.bind(languages[position], position == selectedPosition)
        }

        override fun getItemCount() = languages.size

        inner class LanguageViewHolder(private val binding: ItemLanguageBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(language: Language, isSelected: Boolean) {
                binding.tvLanguageName.text = language.displayName
                binding.tvLanguageNative.text = language.nativeName

                // Update selection state
                binding.root.isSelected = isSelected
                binding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

                binding.root.setOnClickListener {
                    val oldPosition = selectedPosition
                    selectedPosition = adapterPosition

                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)

                    onLanguageSelected(language)
                }
            }
        }
    }
}