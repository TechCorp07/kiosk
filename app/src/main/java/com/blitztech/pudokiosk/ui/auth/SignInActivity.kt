package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.common.AuthStatus
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivitySignInBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.main.CustomerMainActivity
import com.blitztech.pudokiosk.ui.main.CourierMainActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class SignInActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivitySignInBinding
    private lateinit var prefs: Prefs
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


        // Load current language
        val currentLocale = prefs.getLocale()

        // Initialize API repository
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = NetworkModule.provideApiService(retrofit)
        apiRepository = NetworkModule.provideApiRepository(apiService, this)
    }

    private fun setupViews() {
        // Set localized text
        binding.tvWelcomeBack.text = getString(R.string.welcome_back)
        binding.tvSubtitle.text = getString(R.string.sign_in_subtitle)
        binding.etMobileNumber.hint = getString(R.string.mobile_placeholder)
        binding.etPin.hint = getString(R.string.pin_placeholder)
        binding.btnSignIn.text = getString(R.string.sign_in)
        binding.tvForgotPin.text = getString(R.string.forgot_pin)
        binding.tvDontHaveAccount.text = getString(R.string.dont_have_account)
        binding.tvSignUp.text = getString(R.string.sign_up)

        // Set user type specific title
        val userTypeTitle = if (userType == UserType.CUSTOMER) {
            getString(R.string.customer_signin)
        } else {
            getString(R.string.courier_signin)
        }
        binding.tvWelcomeBack.text = userTypeTitle
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSignIn.setOnClickListener {
            if (!isLoading) {
                attemptLogin()
            }
        }

        binding.tvForgotPin.setOnClickListener {
            navigateToForgotPin()
        }

        binding.tvSignUp.setOnClickListener {
            navigateToSignUp()
        }
    }

    private fun attemptLogin() {
        val mobileNumber = binding.etMobileNumber.text.toString().trim()
        val pin = binding.etPin.text.toString().trim()

        // Validation
        if (mobileNumber.isEmpty()) {
            showError(getString(R.string.error_mobile_required))
            return
        }

        if (pin.isEmpty()) {
            showError(getString(R.string.error_pin_required))
            return
        }

        val formattedPhone = ValidationUtils.formatPhoneNumber(mobileNumber)
        if (!ValidationUtils.isValidPhoneNumber(formattedPhone)) {
            showError(getString(R.string.error_invalid_phone))
            return
        }

        if (!ValidationUtils.isValidPin(pin)) {
            showError(getString(R.string.error_invalid_pin))
            return
        }

        performLogin(formattedPhone, pin)
    }

    private fun performLogin(mobileNumber: String, pin: String) {
        setLoading(true)
        lifecycleScope.launch {
            when (val result = apiRepository.login(mobileNumber, pin)) {
                is NetworkResult.Success -> {
                    val response = result.data
                    when (response.status) {
                        AuthStatus.PENDING_OTP -> {
                            navigateToOtpVerification(mobileNumber, response.accessToken)
                        }
                        AuthStatus.AUTHENTICATED -> {
                            // First-time user - needs to change PIN
                            navigateToFirstTimePinChange(response.accessToken)
                        }
                        AuthStatus.FAILED -> {
                            showError(getString(R.string.error_login_failed))
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

    private fun navigateToMainMenu() {
        // Save user authentication data
        prefs.saveAuthData(
            accessToken = "dummy_token", // Replace with actual token
            refreshToken = "dummy_refresh",
            userType = userType.name,
            mobileNumber = binding.etMobileNumber.text.toString().trim(),
            userName = null
        )

        val intent = when (userType) {
            UserType.CUSTOMER -> Intent(this, CustomerMainActivity::class.java)
            UserType.COURIER -> Intent(this, CourierMainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignIn.isEnabled = !loading
        binding.btnSignIn.text = if (loading) {
            getString(R.string.signing_in)
        } else {
            getString(R.string.sign_in)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}