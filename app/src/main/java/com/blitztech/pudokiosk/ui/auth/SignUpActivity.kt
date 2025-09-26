package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.location.CityDto
import com.blitztech.pudokiosk.data.api.dto.location.SuburbDto
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivitySignUpBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.custom.SpinnerItem
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SignUpActivity : BaseKioskActivity() {

    companion object {
        const val EXTRA_USER_TYPE = "extra_user_type"
    }
    private lateinit var binding: ActivitySignUpBinding

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
        setupValidation()
        setupBackButton()
    }

    private fun setupDependencies() {
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
        binding.tilMobileNumber.hint = getString(R.string.mobile_number_format)
        binding.tilNationalId.hint = getString(R.string.national_id_format)

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

    private fun setupValidation() {
        // Name validation
        binding.etName.addTextChangedListener(createTextWatcher(binding.tilName) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilName, "Name is required")
                false
            } else {
                clearFieldError(binding.tilName)
                true
            }
        })

        // Surname validation
        binding.etSurname.addTextChangedListener(createTextWatcher(binding.tilSurname) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilSurname, "Surname is required")
                false
            } else {
                clearFieldError(binding.tilSurname)
                true
            }
        })

        // Email validation
        binding.etEmail.addTextChangedListener(createTextWatcher(binding.tilEmail) { text ->
            if (text.isNotEmpty() && !ValidationUtils.isValidEmail(text)) {
                showFieldError(binding.tilEmail, ValidationUtils.getEmailErrorMessage())
                false
            } else {
                clearFieldError(binding.tilEmail)
                true
            }
        })

        // Phone validation
        binding.etMobileNumber.addTextChangedListener(createTextWatcher(binding.tilMobileNumber) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilMobileNumber, "Mobile number is required")
                false
            } else if (!ValidationUtils.isValidPhoneNumber(text)) {
                showFieldError(binding.tilMobileNumber, ValidationUtils.getPhoneErrorMessage())
                false
            } else {
                clearFieldError(binding.tilMobileNumber)
                true
            }
        })

        // National ID validation
        binding.etNationalId.addTextChangedListener(createTextWatcher(binding.tilNationalId) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilNationalId, "National ID is required")
                false
            } else if (!ValidationUtils.isValidNationalId(text)) {
                showFieldError(binding.tilNationalId, ValidationUtils.getNationalIdErrorMessage())
                false
            } else {
                clearFieldError(binding.tilNationalId)
                true
            }
        })

        // House Number validation
        binding.etHouseNumber.addTextChangedListener(createTextWatcher(binding.tilHouseNumber) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilHouseNumber, "House number is required")
                false
            } else {
                clearFieldError(binding.tilHouseNumber)
                true
            }
        })

        // Street validation
        binding.etStreet.addTextChangedListener(createTextWatcher(binding.tilStreet) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilStreet, "Street is required")
                false
            } else {
                clearFieldError(binding.tilStreet)
                true
            }
        })

        // Suburb validation
        binding.etSuburb.addTextChangedListener(createTextWatcher(binding.tilSuburb) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilSuburb, "Suburb is required")
                false
            } else {
                clearFieldError(binding.tilSuburb)
                true
            }
        })

        // City validation
        binding.etCity.addTextChangedListener(createTextWatcher(binding.tilCity) { text ->
            if (text.isBlank()) {
                showFieldError(binding.tilCity, "City is required")
                false
            } else {
                clearFieldError(binding.tilCity)
                true
            }
        })
    }

    private fun setupBackButton() {
        // For the back button in your layout (top left corner)
        binding.btnBack.setOnClickListener {
            handleBackNavigation()
        }
    }

    private fun createTextWatcher(
        textInputLayout: TextInputLayout,
        validator: (String) -> Boolean
    ): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validator(s.toString().trim())
                updateButtonState()
            }
        }
    }

    private fun showFieldError(textInputLayout: TextInputLayout, message: String) {
        textInputLayout.error = message
        textInputLayout.endIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_error)
        textInputLayout.setEndIconTintList(ContextCompat.getColorStateList(this, R.color.error_red))
    }

    private fun clearFieldError(textInputLayout: TextInputLayout) {
        textInputLayout.error = null
        textInputLayout.endIconDrawable = null
    }

    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener { navigateToPrivacyPolicy() }
        binding.tvSignIn.setOnClickListener { navigateToSignIn() }
    }

    private fun validateForm(): Boolean {
        val name = binding.etName.text.toString().trim()
        val surname = binding.etSurname.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val mobileNumber = binding.etMobileNumber.text.toString().trim()
        val nationalId = binding.etNationalId.text.toString().trim()
        val houseNumber = binding.etHouseNumber.text.toString().trim()
        val street = binding.etStreet.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val suburb = binding.etSuburb.text.toString().trim()

        var isValid = true

        // Validate required fields
        if (name.isEmpty() || surname.isEmpty() || mobileNumber.isEmpty() ||
            nationalId.isEmpty() || houseNumber.isEmpty() || street.isEmpty() ||
            city.isEmpty() || suburb.isEmpty()) {

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

        return isValid
    }

    private fun updateButtonState() {
        val isValid = validateForm()
        binding.btnNext.isEnabled = isValid && !isLoading
        binding.btnNext.alpha = if (isValid && !isLoading) 1.0f else 0.5f
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
