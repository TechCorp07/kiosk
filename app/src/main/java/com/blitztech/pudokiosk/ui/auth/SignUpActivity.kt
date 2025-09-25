package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivitySignUpBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils

class SignUpActivity : BaseKioskActivity() {

    companion object {
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var prefs: Prefs
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
        setupBackButton()
    }

    private fun setupDependencies() {
        prefs = ZimpudoApp.prefs


        // Initialize API repository
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = NetworkModule.provideApiService(retrofit)
        apiRepository = NetworkModule.provideApiRepository(apiService, this)
    }

    private fun setupViews() {
        // Set localized text
        binding.tvTitle.text = getString(R.string.create_account)
        binding.tvSubtitle.text = getString(R.string.sign_up_subtitle)

        // Personal Information section
        binding.tvPersonalInfo.text = getString(R.string.personal_information)
        binding.etName.hint = getString(R.string.name)
        binding.etSurname.hint = getString(R.string.surname)
        binding.etEmail.hint = getString(R.string.email_optional)
        binding.etMobileNumber.hint = getString(R.string.mobile_number_format)
        binding.etNationalId.hint = getString(R.string.national_id_format)

        // Address Information section
        binding.tvAddressInfo.text = getString(R.string.address_information)
        binding.etHouseNumber.hint = getString(R.string.house_number)
        binding.etStreet.hint = getString(R.string.street)
        binding.etSuburb.hint = getString(R.string.suburb)
        binding.etCity.hint = getString(R.string.city)

        // Button text
        binding.btnNext.text = getString(R.string.next)
        binding.tvAlreadyHaveAccount.text = getString(R.string.already_have_account)
        binding.tvSignIn.text = getString(R.string.sign_in)

        // Initially disable next button
        binding.btnNext.isEnabled = false
        binding.btnNext.alpha = 0.5f
    }

    private fun setupBackButton() {
        // For the back button in your layout (top left corner)
        binding.btnBack.setOnClickListener {
            handleBackNavigation()
        }
    }

    private fun setupClickListeners() {
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
            showError(getString(R.string.error_required_field))
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
