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
    private var isLoading = false
    private var registrationStep = 1 // 1 = Register User, 2 = Upload KYC

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
        setupClickListeners()
        startRegistrationProcess()
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
        binding.tvTitle.text = getString(R.string.kyc_upload)
        binding.tvSubtitle.text = getString(R.string.kyc_subtitle)
        binding.tvDocumentType.text = getString(R.string.kyc_national_id)
        binding.btnUploadDocument.text = getString(R.string.upload_document)

        // Show step 1 initially
        updateStepUI(1)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        binding.btnUploadDocument.setOnClickListener {
            if (!isLoading && registrationStep == 2) {
                uploadKycDocument()
            }
        }
    }

    private fun startRegistrationProcess() {
        registerUser()
    }

    private fun registerUser() {
        setLoading(true, getString(R.string.creating_account))
        updateStepUI(1)

        lifecycleScope.launch {
            val result = apiRepository.registerUser(
                name = formData.name,
                surname = formData.surname,
                email = formData.email,
                mobileNumber = formData.mobileNumber,
                nationalId = formData.nationalId,
                houseNumber = formData.houseNumber,
                street = formData.street,
                suburbId = formData.suburbId,
                cityId = formData.cityId
            )

            when (result) {
                is NetworkResult.Success -> {
                    showSuccess(result.data.message)
                    // Move to KYC step
                    registrationStep = 2
                    updateStepUI(2)
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

    private fun uploadKycDocument() {
        setLoading(true, getString(R.string.uploading_document))

        lifecycleScope.launch {
            val result = apiRepository.uploadKyc(formData.mobileNumber)

            when (result) {
                is NetworkResult.Success -> {
                    showSuccess(getString(R.string.kyc_upload_success))
                    // Registration complete, navigate to sign in
                    navigateToSignIn()
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

    private fun updateStepUI(step: Int) {
        when (step) {
            1 -> {
                binding.tvStepIndicator.text = "Step 1 of 2: Creating Account"
                binding.cardKyc.visibility = View.GONE
                binding.btnUploadDocument.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
            }
            2 -> {
                binding.tvStepIndicator.text = "Step 2 of 2: Document Verification"
                binding.cardKyc.visibility = View.VISIBLE
                binding.btnUploadDocument.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
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

    private fun setLoading(loading: Boolean, message: String = "") {
        isLoading = loading
        binding.btnUploadDocument.isEnabled = !loading
        if (loading) {
            binding.tvLoadingMessage.text = message
            binding.tvLoadingMessage.visibility = View.VISIBLE
        } else {
            binding.tvLoadingMessage.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}