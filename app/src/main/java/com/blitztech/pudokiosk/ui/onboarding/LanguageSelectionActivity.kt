package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.databinding.ActivityLanguageSelectionBinding
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

        prefs = Prefs(this)
        i18n = I18n(this)

        setupViews()
        setupLanguageSelection()
    }

    private fun setupViews() {
        // Set initial language (default to English)
        val currentLocale = prefs.getLocale().ifBlank { "en" }
        i18n.load(currentLocale)

        binding.tvTitle.text = i18n.t("select_language", "Select Your Language")
        binding.tvSubtitle.text = i18n.t("language_subtitle", "Choose your preferred language for the app")
        binding.btnContinue.text = i18n.t("continue_button", "Continue")

        // Initially disable continue button
        binding.btnContinue.isEnabled = false
        binding.btnContinue.alpha = 0.5f

        binding.btnContinue.setOnClickListener {
            selectedLanguage?.let { language ->
                prefs.setLocale(language.code)
                i18n.load(language.code)
                navigateToOnboarding()
            }
        }
    }

    private fun setupLanguageSelection() {
        languageAdapter = LanguageAdapter(languages) { language ->
            selectedLanguage = language
            updateContinueButton()
            updateTextsForSelectedLanguage(language.code)
        }

        binding.recyclerLanguages.apply {
            layoutManager = LinearLayoutManager(this@LanguageSelectionActivity)
            adapter = languageAdapter
        }

        // Pre-select current language if set
        val currentLocale = prefs.getLocale()
        if (currentLocale.isNotBlank()) {
            val currentLanguage = languages.find { it.code == currentLocale }
            currentLanguage?.let { language ->
                selectedLanguage = language
                languageAdapter.setSelectedLanguage(language)
                updateContinueButton()
            }
        }
    }

    private fun updateContinueButton() {
        val isEnabled = selectedLanguage != null
        binding.btnContinue.isEnabled = isEnabled
        binding.btnContinue.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun updateTextsForSelectedLanguage(languageCode: String) {
        // Load the selected language and update UI texts
        i18n.load(languageCode)
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