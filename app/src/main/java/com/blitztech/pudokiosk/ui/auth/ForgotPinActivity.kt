package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivityForgotPinBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class ForgotPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPinBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n
    private lateinit var apiRepository: ApiRepository

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.tvTitle.text = i18n.t("forgot_pin", "Forgot PIN?")
        binding.tvSubtitle.text = "Enter your mobile number and we'll send you instructions to reset your PIN"
        binding.etMobileNumber.hint = i18n.t("mobile_placeholder", ApiConfig.PHONE_PLACEHOLDER)
        binding.btnSendInstructions.text = "Send Instructions"
        binding.btnBackToSignIn.text = "Back to Sign In"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnSendInstructions.setOnClickListener {
            if (!isLoading) {
                sendForgotPinRequest()
            }
        }

        binding.btnBackToSignIn.setOnClickListener {
            finish()
        }
    }

    private fun sendForgotPinRequest() {
        val mobileNumber = binding.etMobileNumber.text.toString().trim()

        // Validate mobile number
        if (mobileNumber.isEmpty()) {
            binding.tilMobileNumber.error = i18n.t("error_required_field", "This field is required")
            return
        }

        if (!ValidationUtils.isValidPhoneNumber(mobileNumber)) {
            binding.tilMobileNumber.error = i18n.t("error_invalid_phone", "Please enter a valid phone number")
            return
        }

        binding.tilMobileNumber.error = null
        setLoading(true)

        lifecycleScope.launch {
            when (val result = apiRepository.forgotPin(mobileNumber)) {
                is NetworkResult.Success -> {
                    showSuccess("Instructions sent to your mobile number")
                    finish()
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

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendInstructions.isEnabled = !loading
        binding.btnSendInstructions.text = if (loading) {
            i18n.t("loading", "Loading…")
        } else {
            "Send Instructions"
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}