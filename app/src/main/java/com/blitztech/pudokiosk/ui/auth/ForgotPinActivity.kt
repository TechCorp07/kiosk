package com.blitztech.pudokiosk.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivityForgotPinBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class ForgotPinActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityForgotPinBinding
    private lateinit var prefs: Prefs
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
        prefs = ZimpudoApp.prefs

        // Initialize API repository
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = NetworkModule.provideApiService(retrofit)
        apiRepository = NetworkModule.provideApiRepository(apiService, this)
    }

    private fun setupViews() {
        binding.tvTitle.text = getString(R.string.forgot_pin)
        binding.tvSubtitle.text = getString(R.string.forgot_pin_subtitle)
        binding.etMobileNumber.hint = getString(R.string.mobile_placeholder)
        binding.btnSendInstructions.text = getString(R.string.send_instructions)
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
    }

    private fun sendForgotPinRequest() {
        val mobileNumber = binding.etMobileNumber.text.toString().trim()

        // Validate mobile number
        if (mobileNumber.isEmpty()) {
            getString(R.string.error_required_field)
            return
        }

        if (!ValidationUtils.isValidPhoneNumber(mobileNumber)) {
            getString(R.string.error_invalid_phone)
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
            getString(R.string.loading)
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