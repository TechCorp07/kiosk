package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.data.api.ApiRepository
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.api.dto.auth.AuthStatus
import com.blitztech.pudokiosk.databinding.ActivitySignInBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.main.KioskActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class SignInActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivitySignInBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n
    private lateinit var apiRepository: ApiRepository

    private var userType: UserType = UserType.CUSTOMER
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get user type from intent
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

        // Initialize API repository
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = NetworkModule.provideApiService(retrofit)
        apiRepository = NetworkModule.provideApiRepository(apiService, this)
    }

    private fun setupViews() {
        // Set localized text
        binding.tvWelcomeBack.text = i18n.t("welcome_back", "Welcome Back")
        binding.tvSubtitle.text = i18n.t("sign_in_subtitle", "Sign in to continue")
        binding.etMobileNumber.hint = i18n.t("mobile_placeholder", ApiConfig.PHONE_PLACEHOLDER)
        binding.etPin.hint = i18n.t("pin_placeholder", "Enter your PIN")
        binding.btnSignIn.text = i18n.t("sign_in", "Sign In")
        binding.tvForgotPin.text = i18n.t("forgot_pin", "Forgot PIN?")
        binding.tvDontHaveAccount.text = i18n.t("dont_have_account", "Don't have an account?")
        binding.tvSignUp.text = i18n.t("sign_up", "Sign Up")

        // Update title based on user type
        val titleText = when (userType) {
            UserType.CUSTOMER -> i18n.t("welcome_back", "Welcome Back")
            UserType.COURIER -> i18n.t("welcome_back", "Courier Sign In")
        }
        binding.tvWelcomeBack.text = titleText

        // For couriers, hide sign up option
        if (userType == UserType.COURIER) {
            binding.layoutSignUp.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnSignIn.setOnClickListener {
            if (!isLoading) {
                validateAndSignIn()
            }
        }

        binding.tvForgotPin.setOnClickListener {
            navigateToForgotPin()
        }

        binding.tvSignUp.setOnClickListener {
            navigateToSignUp()
        }

        binding.btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun validateAndSignIn() {
        val mobileNumber = binding.etMobileNumber.text.toString().trim()
        val pin = binding.etPin.text.toString().trim()

        // Clear previous errors
        binding.tilMobileNumber.error = null
        binding.tilPin.error = null

        var hasError = false

        // Validate mobile number
        if (mobileNumber.isEmpty()) {
            binding.tilMobileNumber.error = i18n.t("error_required_field", "This field is required")
            hasError = true
        } else if (!ValidationUtils.isValidPhoneNumber(mobileNumber)) {
            binding.tilMobileNumber.error = i18n.t("error_invalid_phone", "Please enter a valid phone number")
            hasError = true
        }

        // Validate PIN
        if (pin.isEmpty()) {
            binding.tilPin.error = i18n.t("error_required_field", "This field is required")
            hasError = true
        } else if (!ValidationUtils.isValidPin(pin)) {
            binding.tilPin.error = i18n.t("error_invalid_pin", "PIN must be 4-6 digits")
            hasError = true
        }

        if (!hasError) {
            performSignIn(mobileNumber, pin)
        }
    }

    private fun performSignIn(mobileNumber: String, pin: String) {
        setLoading(true)

        lifecycleScope.launch {
            when (val result = apiRepository.login(mobileNumber, pin)) {
                is NetworkResult.Success -> {
                    val response = result.data
                    when (response.status) {
                        AuthStatus.PENDING_OTP -> {
                            // Navigate to OTP verification
                            navigateToOtpVerification(mobileNumber, response.accessToken)
                        }
                        AuthStatus.AUTHENTICATED -> {
                            // First time user - navigate to PIN change
                            navigateToFirstTimePinChange(response.accessToken)
                        }
                        AuthStatus.FAILED -> {
                            showError(i18n.t("error_login_failed", "Login failed. Please check your credentials."))
                        }
                    }
                }
                is NetworkResult.Error -> {
                    showError(result.message)
                }
                is NetworkResult.Loading -> {
                    // Handle loading state if needed
                }
            }
            setLoading(false)
        }
    }

    private fun navigateToOtpVerification(mobileNumber: String, accessToken: String) {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra(OtpVerificationActivity.EXTRA_MOBILE_NUMBER, mobileNumber)
            putExtra(OtpVerificationActivity.EXTRA_ACCESS_TOKEN, accessToken)
            putExtra(OtpVerificationActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToFirstTimePinChange(accessToken: String) {
        val intent = Intent(this, PinChangeActivity::class.java).apply {
            putExtra(PinChangeActivity.EXTRA_ACCESS_TOKEN, accessToken)
            putExtra(PinChangeActivity.EXTRA_IS_FIRST_TIME, true)
            putExtra(PinChangeActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToForgotPin() {
        val intent = Intent(this, ForgotPinActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToSignUp() {
        val intent = Intent(this, SignUpActivity::class.java).apply {
            putExtra(SignUpActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignIn.isEnabled = !loading
        binding.btnSignIn.text = if (loading) {
            i18n.t("signing_in", "Signing in…")
        } else {
            i18n.t("sign_in", "Sign In")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}