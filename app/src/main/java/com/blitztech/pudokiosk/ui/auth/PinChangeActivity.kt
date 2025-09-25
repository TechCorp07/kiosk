package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivityPinChangeBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.CustomerMainActivity
import com.blitztech.pudokiosk.ui.main.CourierMainActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

class PinChangeActivity : BaseKioskActivity() {

    companion object {
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_IS_FIRST_TIME = "extra_is_first_time"
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivityPinChangeBinding
    private lateinit var prefs: Prefs
    private lateinit var apiRepository: ApiRepository

    private var accessToken: String = ""
    private var isFirstTime: Boolean = false
    private var userType: UserType = UserType.CUSTOMER
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fun setupBackPressHandling() { onBackPressedDispatcher.addCallback(this) { finish() } }
        // Get data from intent
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""
        isFirstTime = intent.getBooleanExtra(EXTRA_IS_FIRST_TIME, false)
        val userTypeString = intent.getStringExtra(EXTRA_USER_TYPE) ?: UserType.CUSTOMER.name
        userType = UserType.valueOf(userTypeString)

        setupDependencies()
        setupViews()
        setupClickListeners()
        setupFormValidation()
        setupBackPressHandling()
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
        if (isFirstTime) {
            binding.tvTitle.text = getString(R.string.create_new_pin)
            binding.tvSubtitle.text = getString(R.string.pin_change_subtitle)
            binding.tilOldPin.visibility = View.GONE
        } else {
            binding.tvTitle.text = getString(R.string.change_pin)
            binding.tvTitle.text = getString(R.string.pin_change_subtitle)
            binding.tilOldPin.visibility = View.VISIBLE
            binding.etOldPin.hint = getString(R.string.old_pin)
        }

        binding.etNewPin.hint = getString(R.string.new_pin)
        binding.etConfirmPin.hint = getString(R.string.confirm_pin)
        binding.tvPinRequirements.text = getString(R.string.pin_requirements)
        binding.btnSavePin.text = getString(R.string.save_pin)

        // Initially disable save button
        binding.btnSavePin.isEnabled = false
        binding.btnSavePin.alpha = 0.5f
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSavePin.setOnClickListener {
            if (!isLoading) {
                validateAndChangePin()
            }
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

        binding.etOldPin.addTextChangedListener(textWatcher)
        binding.etNewPin.addTextChangedListener(textWatcher)
        binding.etConfirmPin.addTextChangedListener(textWatcher)
    }

    private fun validateForm() {
        val oldPin = binding.etOldPin.text.toString()
        val newPin = binding.etNewPin.text.toString()
        val confirmPin = binding.etConfirmPin.text.toString()

        var isValid = true

        // Clear previous errors
        binding.tilOldPin.error = null
        binding.tilNewPin.error = null
        binding.tilConfirmPin.error = null

        // Validate old PIN (if not first time)
        if (!isFirstTime && !ValidationUtils.isValidPin(oldPin)) {
            isValid = false
        }

        // Validate new PIN
        if (!ValidationUtils.isValidPin(newPin)) {
            if (newPin.isNotEmpty()) {
                binding.tilNewPin.error = getString(R.string.error_invalid_pin)
            }
            isValid = false
        }

        // Validate confirm PIN
        if (newPin != confirmPin) {
            if (confirmPin.isNotEmpty()) {
                binding.tilConfirmPin.error = getString(R.string.pins_dont_match)
            }
            isValid = false
        }

        // Update button state
        binding.btnSavePin.isEnabled = isValid && !isLoading
        binding.btnSavePin.alpha = if (isValid) 1.0f else 0.5f
    }

    private fun validateAndChangePin() {
        val oldPin = binding.etOldPin.text.toString()
        val newPin = binding.etNewPin.text.toString()
        val confirmPin = binding.etConfirmPin.text.toString()

        // Final validation
        if (!isFirstTime && !ValidationUtils.isValidPin(oldPin)) {
            binding.tilOldPin.error = getString(R.string.error_invalid_pin)
            return
        }

        if (!ValidationUtils.isValidPin(newPin)) {
            binding.tilNewPin.error = getString(R.string.error_invalid_pin)
            return
        }

        if (newPin != confirmPin) {
            binding.tilConfirmPin.error = getString(R.string.pins_dont_match)
            return
        }

        changePin(oldPin, newPin)
    }

    private fun changePin(oldPin: String, newPin: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                // For first time users, use a placeholder old PIN since they don't have one
                val oldPinToUse = if (isFirstTime) "0000" else oldPin

                when (val result = apiRepository.changePin(oldPinToUse, newPin, accessToken)) {
                    is NetworkResult.Success -> {
                        showSuccess(getString(R.string.pin_changed_success))
                        navigateToMainApp()
                    }
                    is NetworkResult.Error -> {
                        showError(result.message)
                    }
                    is NetworkResult.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                showError("Error changing PIN: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun navigateToMainApp() {
        // Store user data after successful PIN change
        if (isFirstTime) {
            prefs.saveAuthData(
                accessToken = accessToken,
                refreshToken = "", // Will be updated when available
                userType = userType.name,
                mobileNumber = "", // Will be populated from previous screen
                userName = null
            )
        }

        // Navigate to appropriate main activity based on user type
        val intent = when (userType) {
            UserType.CUSTOMER -> Intent(this, CustomerMainActivity::class.java)
            UserType.COURIER -> Intent(this, CourierMainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSavePin.isEnabled = !loading
        binding.btnSavePin.text = if (loading) {
            getString(R.string.loading)
        } else {
            getString(R.string.save_pin)
        }

        // Disable form fields during loading
        binding.etOldPin.isEnabled = !loading
        binding.etNewPin.isEnabled = !loading
        binding.etConfirmPin.isEnabled = !loading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}