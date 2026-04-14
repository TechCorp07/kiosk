package com.blitztech.pudokiosk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkModule
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.ActivityRegistrationProcessBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.onboarding.UserType
import kotlinx.coroutines.launch

class RegistrationProcessActivity : BaseKioskActivity() {

    companion object {
        const val EXTRA_FORM_DATA = "extra_form_data"
        const val EXTRA_USER_TYPE = "extra_user_type"
    }

    private lateinit var binding: ActivityRegistrationProcessBinding
    private lateinit var prefs: Prefs
    private lateinit var apiRepository: ApiRepository

    private lateinit var formData: SignUpFormData
    private var userType: UserType = UserType.CUSTOMER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        formData = intent.getParcelableExtra(EXTRA_FORM_DATA) ?: return
        val userTypeString = intent.getStringExtra(EXTRA_USER_TYPE) ?: UserType.CUSTOMER.name
        userType = UserType.valueOf(userTypeString)

        setupDependencies()
        setupViews()
        registerUser()
    }

    private fun setupDependencies() {
        prefs = ZimpudoApp.prefs
        apiRepository = ZimpudoApp.apiRepository
    }

    private fun setupViews() {
        binding.tvTitle.text = getString(R.string.creating_account)
        binding.tvLoadingMessage.text = getString(R.string.creating_account)
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnRetry.setOnClickListener {
            binding.llErrorState.visibility = View.GONE
            registerUser()
        }
    }

    private fun registerUser() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvLoadingMessage.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Walk-in kiosk registration uses /users/partial (no KYC required)
            val result = apiRepository.partialRegisterUser(
                name = formData.name,
                surname = formData.surname,
                email = formData.email,
                mobileNumber = formData.mobileNumber,
                nationalId = formData.nationalId,
                houseNumber = formData.houseNumber,
                street = formData.street,
                suburb = formData.suburb,
                city = formData.city
            )

            when (result) {
                is NetworkResult.Success -> {
                    showSuccess(result.data.message)
                    navigateToSignIn()
                }
                is NetworkResult.Error -> {
                    showError(result.message)
                    binding.progressBar.visibility = View.GONE
                    binding.tvLoadingMessage.visibility = View.GONE
                    
                    binding.llErrorState.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = result.message
                }
                is NetworkResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    private fun navigateToSignIn() {
        Toast.makeText(this,
            getString(R.string.success_registration),
            Toast.LENGTH_LONG).show()

        val intent = Intent(this, SignInActivity::class.java).apply {
            putExtra(SignInActivity.EXTRA_USER_TYPE, userType.name)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}