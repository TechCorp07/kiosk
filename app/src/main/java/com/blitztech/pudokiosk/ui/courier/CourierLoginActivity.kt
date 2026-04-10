package com.blitztech.pudokiosk.ui.courier

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.common.AuthStatus
import com.blitztech.pudokiosk.databinding.ActivityCourierLoginBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.auth.OtpVerificationActivity
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.CourierMainActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import kotlinx.coroutines.launch

/**
 * Courier Authentication Screen
 *
 * Flow:
 * 1. Courier enters mobile number + 4-digit PIN
 * 2. App calls login API
 * 3. On PENDING_OTP: navigates to OtpVerificationActivity to complete 2FA
 * 4. On AUTHENTICATED: saves session → launches CourierMainActivity
 * 5. On FAILED / Error: shows error message, allows retry
 */
class CourierLoginActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierLoginBinding
    private lateinit var prefs: Prefs
    private val api by lazy { ZimpudoApp.apiRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs
        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        binding.etMobile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilMobile.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.etPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilPin.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finishSafely() }

        binding.btnLogin.setOnClickListener {
            val mobile = binding.etMobile.text.toString().trim()
            val pin = binding.etPin.text.toString().trim()
            if (!validate(mobile, pin)) return@setOnClickListener
            attemptLogin(mobile, pin)
        }
    }

    private fun validate(mobile: String, pin: String): Boolean {
        var ok = true
        if (mobile.length < 9) {
            binding.tilMobile.error = "Enter a valid mobile number"
            ok = false
        }
        if (pin.length != 6) {
            binding.tilPin.error = "PIN must be 6 digits"
            ok = false
        }
        return ok
    }

    private fun attemptLogin(mobile: String, pin: String) {
        setLoading(true)
        lifecycleScope.launch {
            val result = api.login(mobile, pin)
            setLoading(false)
            when (result) {
                is NetworkResult.Success -> {
                    val resp = result.data
                    when (resp.status) {
                        AuthStatus.PENDING_OTP -> {
                            // OTP sent — navigate to verification screen
                            navigateToOtpVerification(mobile, pin, resp.accessToken)
                        }
                        AuthStatus.AUTHENTICATED -> {
                            // Direct authentication (no OTP required for this account)
                            prefs.saveAuthData(
                                accessToken = resp.accessToken.orEmpty(),
                                refreshToken = resp.refreshToken.orEmpty(),
                                userType = "COURIER",
                                mobileNumber = mobile,
                                userName = ""
                            )
                            SecurityCameraManager.getInstance(this@CourierLoginActivity)
                                .captureSecurityPhoto(
                                    reason = PhotoReason.COURIER_LOGIN,
                                    referenceId = mobile,
                                    userId = mobile
                                )
                            navigateToDashboard()
                        }
                        AuthStatus.FAILED -> {
                            showError("Invalid mobile number or PIN. Please try again.")
                        }
                        else -> {
                            showError("Unexpected authentication response. Please try again.")
                        }
                    }
                }
                is NetworkResult.Error -> {
                    showError("Login failed: ${result.message}")
                }
                is NetworkResult.Loading<*> -> { /* handled by setLoading */ }
            }
        }
    }

    private fun navigateToOtpVerification(mobile: String, pin: String, accessToken: String?) {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra(OtpVerificationActivity.EXTRA_MOBILE_NUMBER, mobile)
            putExtra(OtpVerificationActivity.EXTRA_PIN, pin)
            accessToken?.let { putExtra(OtpVerificationActivity.EXTRA_ACCESS_TOKEN, it) }
            putExtra(OtpVerificationActivity.EXTRA_USER_TYPE, UserType.COURIER.name)
        }
        startActivity(intent)
        finishSafely()
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, CourierMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finishSafely()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.etMobile.isEnabled = !loading
        binding.etPin.isEnabled = !loading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

