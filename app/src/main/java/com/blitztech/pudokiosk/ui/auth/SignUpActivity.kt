package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivitySignUpBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n
    private lateinit var apiRepository: ApiRepository

    private var userType: UserType = UserType.CUSTOMER
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get user type from intent
        val userTypeString = intent.getStringExtra(EXTRA_USER_TYPE) ?: UserType.CUSTOMER.name
        userType = UserType.valueOf(userTypeString)

        setupDependencies()
        setupViews()
        setupClickListeners()
        setupFormValidation()
    }

    private fun setupDependencies() {
        prefs = Prefs(this)
        i18n = I18n(this)

        // Load current language
        val currentLocale = prefs.getLocale()
        i18n.load(currentLocale)

        // Initialize API repository
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = NetworkModule.provideApiService(retrofit)
        apiRepository = NetworkModule.provideApiRepository(apiService, this)
    }

    private fun setupViews() {
        // Set localized text
        binding.tvTitle.text = i18n.t("create_account", "Create Account")
        binding.tvSubtitle.text = i18n.t("sign_up_subtitle", "Sign up to get started")

        // Personal Information section
        binding.tvPersonalInfo.text = i18n.t("personal_information", "Personal Information")
        binding.etName.hint = i18n.t("name", "Name")
        binding.etSurname.hint = i18n.t("surname", "Surname")
        binding.etEmail.hint = i18n.t("email_optional", "Email (Optional)")
        binding.etMobileNumber.hint = i18n.t("mobile_number_format", "Format: +2637XXXXXXXX")
        binding.etNationalId.hint = i18n.t("national_id_format", "Format: XX-XXXXXXX A XX")

        // Address Information section
        binding.tvAddressInfo.text = i18n.t("address_information", "Address Information")
        binding.etHouseNumber.hint = i18n.t("house_number", "House Number")
        binding.etStreet.hint = i18n.t("street", "Street")
        binding.etSuburb.hint = i18n.t("suburb", "Suburb")
        binding.etCity.hint = i18n.t("city", "City")

        // Button text
        binding.btnNext.text = i18n.t("next", "Next")
        binding.tvAlreadyHaveAccount.text = i18n.t("already_have_account", "Already have an account?")
        binding.tvSignIn.text = i18n.t("sign_in", "Sign In")

        // Initially disable next button
        binding.btnNext.isEnabled = false
        binding.btnNext.alpha = 0.5f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnNext.setOnClickListener {
            if (!isLoading) {
                navigateToPrivacyPolicy()
            }
        }

        binding.layoutSignIn.setOnClickListener {
            navigateToSignIn()
        }
    }

    private fun setupFormValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateForm()
            }
        }

        // Add text watchers to required fields
        binding.etName.addTextChangedListener(textWatcher)
        binding.etSurname.addTextChangedListener(textWatcher)
        binding.etMobileNumber.addTextChangedListener(textWatcher)
        binding.etNationalId.addTextChangedListener(textWatcher)
        binding.etHouseNumber.addTextChangedListener(textWatcher)
        binding.etStreet.addTextChangedListener(textWatcher)
        binding.etSuburb.addTextChangedListener(textWatcher)
        binding.etCity.addTextChangedListener(textWatcher)

        // Auto-format phone number
        binding.etMobileNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.let { editable ->
                    val formatted = ValidationUtils.formatPhoneNumber(editable.toString())
                    if (formatted != editable.toString()) {
                        binding.etMobileNumber.removeTextChangedListener(this)
                        binding.etMobileNumber.setText(formatted)
                        binding.etMobileNumber.setSelection(formatted.length)
                        binding.etMobileNumber.addTextChangedListener(this)
                    }
                }
            }
        })
    }

    private fun validateForm(): Boolean {
        val name = binding.etName.text.toString().trim()
        val surname = binding.etSurname.text.toString().trim()
        val mobileNumber = binding.etMobileNumber.text.toString().trim()
        val nationalId = binding.etNationalId.text.toString().trim()
        val houseNumber = binding.etHouseNumber.text.toString().trim()
        val street = binding.etStreet.text.toString().trim()
        val suburb = binding.etSuburb.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        var isValid = true

        // Validate required fields
        if (name.isEmpty() || surname.isEmpty() || mobileNumber.isEmpty() ||
            nationalId.isEmpty() || houseNumber.isEmpty() || street.isEmpty() ||
            suburb.isEmpty() || city.isEmpty()) {
            isValid = false
        }

        // Validate phone number format
        if (mobileNumber.isNotEmpty() && !ValidationUtils.isValidPhoneNumber(mobileNumber)) {
            isValid = false
        }

        // Validate email format if provided
        if (email.isNotEmpty() && !ValidationUtils.isValidEmail(email)) {
            isValid = false
        }

        // Validate national ID format
        if (nationalId.isNotEmpty() && !ValidationUtils.isValidNationalId(nationalId)) {
            isValid = false
        }

        // Update button state
        binding.btnNext.isEnabled = isValid && !isLoading
        binding.btnNext.alpha = if (isValid) 1.0f else 0.5f

        return isValid
    }

    private fun navigateToPrivacyPolicy() {
        if (!validateForm()) {
            showError(i18n.t("error_required_field", "Please fill in all required fields correctly"))
            return
        }

        // Store form data temporarily and navigate to privacy policy
        val formData = SignUpFormData(
            name = binding.etName.text.toString().trim(),
            surname = binding.etSurname.text.toString().trim(),
            email = binding.etEmail.text.toString().trim(),
            mobileNumber = binding.etMobileNumber.text.toString().trim(),
            nationalId = binding.etNationalId.text.toString().trim(),
            houseNumber = binding.etHouseNumber.text.toString().trim(),
            street = binding.etStreet.text.toString().trim(),
            suburb = binding.etSuburb.text.toString().trim(),
            city = binding.etCity.text.toString().trim()
        )

        val intent = Intent(this, PrivacyPolicyActivity::class.java).apply {
            putExtra(PrivacyPolicyActivity.EXTRA_FORM_DATA, formData)
            putExtra(PrivacyPolicyActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
    }

    private fun navigateToSignIn() {
        val intent = Intent(this, SignInActivity::class.java).apply {
            putExtra(SignInActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
