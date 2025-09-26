package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.common.AuthStatus
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivityOtpVerificationBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.CustomerMainActivity
import com.blitztech.pudokiosk.ui.main.CourierMainActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class OtpVerificationActivity : BaseKioskActivity() {

    companion object {
        const val EXTRA_MOBILE_NUMBER = "extra_mobile_number"
        const val EXTRA_PIN = "extra_pin"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_USER_TYPE = "extra_user_type"
        private const val COUNTDOWN_TIMER_SECONDS = 120L
    }

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var prefs: Prefs
    private lateinit var apiRepository: ApiRepository

    private var mobileNumber: String = ""
    private var pin: String = ""
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
        pin = intent.getStringExtra(EXTRA_PIN) ?: ""
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""
        val userTypeString = intent.getStringExtra(EXTRA_USER_TYPE) ?: UserType.CUSTOMER.name
        userType = UserType.valueOf(userTypeString)

        setupDependencies()
        setupViews()
        setupClickListeners()
        setupOtpInput()
        startCountdownTimer()
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
        binding.tvTitle.text = getString(R.string.verify_otp)
        binding.tvSubtitle.text = getString(R.string.otp_sent)
        binding.tvMobileNumber.text = mobileNumber
        binding.etOtp.hint = getString(R.string.otp_placeholder)
        binding.btnVerify.text = getString(R.string.verify)
        binding.btnResend.text = getString(R.string.resend_otp)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnVerify.setOnClickListener {
            if (!isLoading) {
                attemptOtpVerification()
            }
        }

        binding.btnResend.setOnClickListener {
            if (binding.btnResend.isEnabled) {
                resendOtp()
            }
        }
    }

    private fun setupOtpInput() {
        binding.etOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val otp = s.toString().trim()
                binding.btnVerify.isEnabled = otp.length == 6 && !isLoading

                // Auto-verify when 6 digits are entered
                if (otp.length == 6 && !isLoading) {
                    attemptOtpVerification()
                }
            }
        })
    }

    private fun startCountdownTimer() {
        countDownTimer?.cancel()

        binding.btnResend.isEnabled = false

        countDownTimer = object : CountDownTimer(COUNTDOWN_TIMER_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60

                // Format time as MM:SS or just SS if less than 1 minute
                val timeText = if (minutes > 0) {
                    String.format(getString(R.string.resend_in_minutes), minutes, remainingSeconds)
                } else {
                    String.format(getString(R.string.resend_in), remainingSeconds)
                }

                binding.btnResend.text = timeText
            }

            override fun onFinish() {
                // Simply enable the button - styling handled by selector
                binding.btnResend.isEnabled = true
                binding.btnResend.text = getString(R.string.resend_otp)
            }
        }.start()
    }

    private fun attemptOtpVerification() {
        val otp = binding.etOtp.text.toString().trim()

        if (otp.isEmpty()) {
            showError(getString(R.string.error_otp_required))
            return
        }

        if (!ValidationUtils.isValidOtp(otp)) {
            showError(getString(R.string.error_invalid_otp))
            return
        }

        performOtpVerification(otp)
    }

    private fun performOtpVerification(otp: String) {
        setLoading(true)
        lifecycleScope.launch {
            when (val result = apiRepository.verifyOtp(mobileNumber, otp)) {
                is NetworkResult.Success -> {
                    val response = result.data
                    when (response.status) {
                        AuthStatus.AUTHENTICATED -> {
                            // Save authentication data and navigate to main menu
                            saveUserDataAndNavigate(response.accessToken, response.refreshToken)
                        }
                        AuthStatus.FAILED -> {
                            showError(getString(R.string.error_invalid_otp))
                            binding.etOtp.text?.clear()
                            binding.etOtp.requestFocus()
                        }
                    }
                }
                is NetworkResult.Error -> {
                    showError(result.message)
                    binding.etOtp.text?.clear()
                    binding.etOtp.requestFocus()
                }
                is NetworkResult.Loading -> {
                    // Handle loading state if needed
                }
            }
            setLoading(false)
        }
    }

    private fun saveUserDataAndNavigate(accessToken: String, refreshToken: String) {
        // Save user authentication data
        prefs.saveAuthData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userType = userType.name,
            mobileNumber = mobileNumber,
            userName = null
        )

        // Navigate to appropriate main menu
        val intent = when (userType) {
            UserType.CUSTOMER -> Intent(this, CustomerMainActivity::class.java)
            UserType.COURIER -> Intent(this, CourierMainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun resendOtp() {
        if (pin.isEmpty()) {
            showError("Unable to resend OTP. Please go back and try again.")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            when (val result = apiRepository.login(mobileNumber, pin)) {
                is NetworkResult.Success -> {
                    val response = result.data
                    when (response.status) {
                        AuthStatus.PENDING_OTP -> {
                            // OTP resent successfully
                            startCountdownTimer()
                            Toast.makeText(this@OtpVerificationActivity,
                                getString(R.string.otp_resent),
                                Toast.LENGTH_SHORT).show()
                        }
                        AuthStatus.AUTHENTICATED -> {
                            // User is now authenticated (edge case)
                            saveUserDataAndNavigate(
                                response.accessToken ?: "",
                                response.refreshToken ?: ""
                            )
                        }
                        AuthStatus.FAILED -> {
                            showError("Failed to resend OTP. Please check your credentials.")
                        }
                        else -> {
                            showError("Unexpected response. Please try again.")
                        }
                    }
                }
                is NetworkResult.Error -> {
                    showError("Failed to resend OTP: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // Handle loading state if needed
                }
            }
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerify.isEnabled = !loading && binding.etOtp.text.toString().length == 6
        binding.btnResend.isEnabled = !loading && binding.btnResend.isEnabled // Respect countdown state

        binding.btnVerify.text = if (loading) {
            getString(R.string.verifying)
        } else {
            getString(R.string.verify)
        }

        // Don't change resend button text if countdown is active
        if (!loading && binding.btnResend.text.toString().startsWith("Resend in")) {
            // Keep countdown text
        } else if (!loading) {
            binding.btnResend.text = getString(R.string.resend_otp)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}