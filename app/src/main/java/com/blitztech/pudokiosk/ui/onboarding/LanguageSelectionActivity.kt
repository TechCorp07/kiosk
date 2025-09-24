package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.databinding.ActivityLanguageSelectionBinding
import com.blitztech.pudokiosk.databinding.ItemLanguageBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs

class LanguageSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageSelectionBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n
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
        prefs = Prefs(this)
        i18n = I18n(this)

        // Start with default English
        i18n.load("en")
    }

    private fun setupViews() {
        binding.tvTitle.text = i18n.t("select_language", "Select Your Language")
        binding.tvSubtitle.text = i18n.t("language_subtitle", "Choose your preferred language for the app")
        binding.btnContinue.text = i18n.t("continue_button", "Continue")
        binding.btnContinue.isEnabled = false
    }

    private fun setupRecyclerView() {
        languageAdapter = LanguageAdapter(languages) { language ->
            selectedLanguage = language
            binding.btnContinue.isEnabled = true

            // Load the selected language immediately for preview
            i18n.load(language.code)
            updateUIText()
        }

        binding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@LanguageSelectionActivity)
            adapter = languageAdapter
        }

        // Select English by default
        selectedLanguage = languages.first { it.code == "en" }
        languageAdapter.setSelectedLanguage(selectedLanguage!!)
        binding.btnContinue.isEnabled = true
    }

    private fun setupClickListeners() {
        binding.btnContinue.setOnClickListener {
            selectedLanguage?.let { language ->
                // Save language preference
                prefs.setLocale(language.code)

                // Navigate to onboarding
                navigateToOnboarding()
            }
        }
    }

    private fun updateUIText() {
        binding.tvTitle.text = i18n.t("select_language", "Select Your Language")
        binding.tvSubtitle.text = i18n.t("language_subtitle", "Choose your preferred language for the app")
        binding.btnContinue.text = i18n.t("continue_button", "Continue")
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }
}

/**
 * Language selection adapter
 */
class LanguageAdapter(
    private val languages: List<Language>,
    private val onLanguageSelected: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>()
 {

    private var selectedPosition = 0

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

    fun setSelectedLanguage(language: Language) {
        val newPosition = languages.indexOf(language)
        if (newPosition != -1) {
            val oldPosition = selectedPosition
            selectedPosition = newPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
        }
    }

    inner class LanguageViewHolder(
        private val binding: ItemLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(language: Language, isSelected: Boolean) {
            binding.tvLanguageName.text = language.displayName
            binding.tvLanguageNative.text = language.nativeName
            binding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
                onLanguageSelected(language)
            }

            // Update card appearance based on selection
            val cardColor = if (isSelected) {
                ContextCompat.getColor(binding.root.context, R.color.zimpudo_primary_light)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.white)
            }
            binding.cardLanguage.setCardBackgroundColor(cardColor)
        }
    }
}