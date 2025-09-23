package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.data.api.ApiRepository
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.auth.AuthStatus
import com.blitztech.pudokiosk.databinding.ActivityOtpVerificationBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.main.KioskActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOBILE_NUMBER = "extra_mobile_number"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_USER_TYPE = "extra_user_type"
        private const val COUNTDOWN_TIMER_SECONDS = 60L
    }

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n
    private lateinit var apiRepository: ApiRepository

    private var mobileNumber: String = ""
    private var accessToken: String = ""
    private var userType: UserType = UserType.CUSTOMER
    private var isLoading = false
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        mobileNumber = intent.getStringExtra(EXTRA_MOBILE_NUMBER) ?: ""
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""
        val userTypeString = intent.getStringExtra(EXTRA_USER_TYPE) ?: UserType.CUSTOMER.name
        userType = UserType.valueOf(userTypeString)

        setupDependencies()
        setupViews()
        setupClickListeners()
        setupOtpInputWatcher()
        startCountdownTimer()
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
        binding.tvTitle.text = i18n.t("verify_otp", "Verify OTP")
        binding.tvSubtitle.text = i18n.t("otp_sent", "We've sent a verification code to your mobile number")
        binding.tvMobileNumber.text = mobileNumber
        binding.etOtp.hint = i18n.t("otp_placeholder", "Enter 6-digit code")
        binding.btnVerify.text = i18n.t("verify", "Verify")
        binding.tvResendOtp.text = i18n.t("resend_otp", "Resend OTP")

        // Initially disable verify button
        binding.btnVerify.isEnabled = false
        binding.btnVerify.alpha = 0.5f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnVerify.setOnClickListener {
            if (!isLoading) {
                verifyOtp()
            }
        }

        binding.tvResendOtp.setOnClickListener {
            if (!isLoading) {
                resendOtp()
            }
        }
    }

    private fun setupOtpInputWatcher() {
        binding.etOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val otpLength = s?.length ?: 0
                val isValidLength = otpLength == 6

                binding.btnVerify.isEnabled = isValidLength && !isLoading
                binding.btnVerify.alpha = if (isValidLength) 1.0f else 0.5f

                // Auto-verify when 6 digits are entered
                if (isValidLength && !isLoading) {
                    verifyOtp()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun verifyOtp() {
        val otp = binding.etOtp.text.toString().trim()

        if (!ValidationUtils.isValidOtp(otp)) {
            binding.tilOtp.error = i18n.t("error_invalid_otp", "Please enter a valid OTP")
            return
        }

        binding.tilOtp.error = null
        setLoading(true)

        lifecycleScope.launch {
            when (val result = apiRepository.verifyOtp(mobileNumber, otp)) {
                is NetworkResult.Success -> {
                    val response = result.data
                    when (response.status) {
                        AuthStatus.AUTHENTICATED -> {
                            // Store tokens and navigate to main app
                            storeAuthTokens(response.accessToken, response.refreshToken)
                            navigateToMainApp()
                        }
                        AuthStatus.FAILED -> {
                            showError(i18n.t("error_invalid_otp", "Invalid OTP. Please try again."))
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

    private fun resendOtp() {
        setLoading(true)

        lifecycleScope.launch {
            // For resend, we'll use the login endpoint again
            when (val result = apiRepository.login(mobileNumber, "")) {
                is NetworkResult.Success -> {
                    showSuccess(i18n.t("success_otp_sent", "OTP sent successfully"))
                    startCountdownTimer()
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

    private fun startCountdownTimer() {
        binding.tvResendOtp.isEnabled = false
        binding.tvResendOtp.alpha = 0.5f

        countDownTimer = object : CountDownTimer(COUNTDOWN_TIMER_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                binding.tvResendOtp.text = "Resend OTP (${secondsRemaining}s)"
            }

            override fun onFinish() {
                binding.tvResendOtp.isEnabled = true
                binding.tvResendOtp.alpha = 1.0f
                binding.tvResendOtp.text = i18n.t("resend_otp", "Resend OTP")
            }
        }.start()
    }

    private fun storeAuthTokens(accessToken: String, refreshToken: String) {
        // Store tokens in secure preferences
        prefs.setAccessToken(accessToken)
        prefs.setRefreshToken(refreshToken)
        prefs.setUserType(userType.name)
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, KioskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerify.isEnabled = !loading && binding.etOtp.text.length == 6
        binding.btnVerify.text = if (loading) {
            i18n.t("verifying_otp", "Verifying OTP…")
        } else {
            i18n.t("verify", "Verify")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}