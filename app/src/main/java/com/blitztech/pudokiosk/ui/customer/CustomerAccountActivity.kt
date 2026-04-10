package com.blitztech.pudokiosk.ui.customer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.user.ProfileAddressDto
import com.blitztech.pudokiosk.data.api.dto.user.UserProfileUpdateRequest
import com.blitztech.pudokiosk.databinding.ActivityCustomerAccountBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.launch

/**
 * Customer Account Management Screen
 *
 * Fetches user profile from GET /api/v1/users/profile
 * Saves profile updates to PUT /api/v1/users/profile
 * Allows PIN change via POST /api/v1/auth/change-pin
 */
class CustomerAccountActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCustomerAccountBinding
    private lateinit var prefs: Prefs
    private val api by lazy { ZimpudoApp.apiRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

        setupViews()
        setupClickListeners()
        fetchProfile()
    }

    private fun setupViews() {
        // Clear fields initially or show loading placeholder
        binding.etName.setText("")
        binding.etSurname.setText("")
        binding.etMobileNumber.setText("")
        binding.etEmail.setText("")
        binding.etNationalId.setText("")
        binding.etHouseNumber.setText("")
        binding.etStreet.setText("")
        binding.etSuburb.setText("")
        binding.etCity.setText("")
        
        // Let's populate what we know from prefs immediately for a quick perceived load
        binding.etName.setText(prefs.getUserName() ?: "")
        binding.etMobileNumber.setText(prefs.getUserMobile() ?: "")
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finishSafely() }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        binding.btnChangePin.setOnClickListener {
            val oldPin = binding.etOldPin.text.toString().trim()
            val newPin = binding.etNewPin.text.toString().trim()
            val confirmPin = binding.etConfirmPin.text.toString().trim()

            if (!validatePin(oldPin, newPin, confirmPin)) return@setOnClickListener
            changePin(oldPin, newPin)
        }
    }

    private fun fetchProfile() {
        setProfileLoading(true)
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setProfileLoading(false)
                showToast("Session expired. Please log in again.")
                return@launch
            }

            when (val result = api.getUserProfile(token)) {
                is NetworkResult.Success -> {
                    setProfileLoading(false)
                    val profile = result.data
                    binding.etName.setText(profile.name ?: "")
                    binding.etSurname.setText(profile.surname ?: "")
                    binding.etMobileNumber.setText(profile.mobileNumber ?: "")
                    binding.etEmail.setText(profile.email ?: "")
                    binding.etNationalId.setText(profile.nationalId ?: "")
                    
                    profile.address?.let { addr ->
                        binding.etHouseNumber.setText(addr.houseNumber ?: "")
                        binding.etStreet.setText(addr.street ?: "")
                        binding.etSuburb.setText(addr.suburb ?: "")
                        binding.etCity.setText(addr.city ?: "")
                    }
                }
                is NetworkResult.Error -> {
                    setProfileLoading(false)
                    showToast("Failed to load profile: ${result.message}")
                }
                is NetworkResult.Loading<*> -> { /* handled */ }
            }
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val surname = binding.etSurname.text.toString().trim()
        val mobile = binding.etMobileNumber.text.toString().trim()
        val nationalId = binding.etNationalId.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if (name.isEmpty() || surname.isEmpty() || mobile.isEmpty() || nationalId.isEmpty()) {
            showToast("Name, Surname, Mobile, and National ID are required.")
            return
        }

        val request = UserProfileUpdateRequest(
            name = name,
            surname = surname,
            email = email,
            nationalId = nationalId,
            address = ProfileAddressDto(
                city = binding.etCity.text.toString().trim(),
                suburb = binding.etSuburb.text.toString().trim(),
                street = binding.etStreet.text.toString().trim(),
                houseNumber = binding.etHouseNumber.text.toString().trim()
            )
        )

        setProfileLoading(true)
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setProfileLoading(false)
                showToast("Session expired. Please log in again.")
                return@launch
            }

            when (val result = api.updateUserProfile(request, token)) {
                is NetworkResult.Success -> {
                    setProfileLoading(false)
                    showToast("Profile updated successfully!")
                    // Update prefs as well just in case they are used elsewhere
                    prefs.putString("user_name", name)
                    prefs.saveUserMobile(mobile)
                }
                is NetworkResult.Error -> {
                    setProfileLoading(false)
                    showToast("Failed to update profile: ${result.message}")
                }
                is NetworkResult.Loading<*> -> { /* handled */ }
            }
        }
    }

    private fun validatePin(old: String, new: String, confirm: String): Boolean {
        var ok = true
        if (old.length != 6) {
            binding.tilOldPin.error = "Current PIN must be 6 digits"
            ok = false
        } else binding.tilOldPin.error = null

        if (new.length != 6) {
            binding.tilNewPin.error = "New PIN must be 6 digits"
            ok = false
        } else binding.tilNewPin.error = null

        if (new != confirm) {
            binding.tilConfirmPin.error = "PINs do not match"
            ok = false
        } else binding.tilConfirmPin.error = null

        return ok
    }

    private fun changePin(oldPin: String, newPin: String) {
        setPinLoading(true)
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setPinLoading(false)
                showToast("Session expired. Please log in again.")
                return@launch
            }

            when (val result = api.changePin(oldPin, newPin, token)) {
                is NetworkResult.Success -> {
                    setPinLoading(false)
                    showToast("PIN changed successfully!")
                    binding.etOldPin.text?.clear()
                    binding.etNewPin.text?.clear()
                    binding.etConfirmPin.text?.clear()
                }
                is NetworkResult.Error -> {
                    setPinLoading(false)
                    showToast("Failed to change PIN: ${result.message}")
                }
                is NetworkResult.Loading<*> -> { /* handled */ }
            }
        }
    }

    private fun setProfileLoading(loading: Boolean) {
        binding.progressBarProfile.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSaveProfile.isEnabled = !loading
    }

    private fun setPinLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnChangePin.isEnabled = !loading
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
