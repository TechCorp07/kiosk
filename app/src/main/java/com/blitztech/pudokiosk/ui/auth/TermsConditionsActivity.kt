package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.databinding.ActivityTermsConditionsBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.onboarding.UserType

class TermsConditionsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORM_DATA = "extra_form_data"
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivityTermsConditionsBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n

    private lateinit var formData: SignUpFormData
    private var userType: UserType = UserType.CUSTOMER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsConditionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        formData = intent.getParcelableExtra(EXTRA_FORM_DATA) ?: return
        val userTypeString = intent.getStringExtra(EXTRA_USER_TYPE) ?: UserType.CUSTOMER.name
        userType = UserType.valueOf(userTypeString)

        setupDependencies()
        setupViews()
        setupClickListeners()
    }

    private fun setupDependencies() {
        prefs = Prefs(this)
        i18n = I18n(this)

        // Load current language
        val currentLocale = prefs.getLocale()
        i18n.load(currentLocale)
    }

    private fun setupViews() {
        binding.tvTitle.text = i18n.t("terms_conditions", "Terms and Conditions")
        binding.tvEffectiveDate.text = i18n.t("effective_date", "Effective Date: 01 July 2025")
        binding.btnAcceptContinue.text = i18n.t("accept_continue", "Accept and Continue")

        // Set terms content (same as privacy policy for now)
        binding.tvTermsContent.text = getTermsConditionsContent()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnAcceptContinue.setOnClickListener {
            navigateToRegistration()
        }
    }

    private fun getTermsConditionsContent(): String {
        return """
TERMS AND CONDITIONS

Welcome to ZIMPUDO. By using our mobile application, you agree to comply with and be bound by the following terms and conditions. Please read them carefully.

1. Acceptance of Terms
By registering for and using the application, you acknowledge that you have read, understood, and agree to be bound by these Terms of Service.

2. User Responsibilities
You must provide accurate and complete information during registration.
You are responsible for maintaining the confidentiality of your account and password.
You agree to notify us immediately of any unauthorized use of your account.

3. Service Description
ZIMPUDO provides a platform for users to send and receive packages. We reserve the right to modify or discontinue the service at any time.

4. Fees
Fees for services will be outlined in the app. By using our services, you agree to pay the applicable fees.

5. Termination
We may suspend or terminate your access to the application if you violate any terms of this agreement.

6. Limitation of Liability
In no event shall ZIMPUDO be liable for any indirect, incidental, or consequential damages arising out of your use of the application.

7. Changes to Terms
We may update these Terms of Service from time to time. Changes will be posted in the app, and your continued use constitutes acceptance of the new terms.
        """.trimIndent()
    }

    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationProcessActivity::class.java).apply {
            putExtra(RegistrationProcessActivity.EXTRA_FORM_DATA, formData)
            putExtra(RegistrationProcessActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
        finish()
    }
}