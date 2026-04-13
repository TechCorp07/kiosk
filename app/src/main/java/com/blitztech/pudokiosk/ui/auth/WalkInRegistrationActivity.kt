package com.blitztech.pudokiosk.ui.auth

import com.blitztech.pudokiosk.R

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.databinding.ActivityWalkInRegistrationBinding
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.sendpackage.SendPackageActivity
import kotlinx.coroutines.launch

class WalkInRegistrationActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityWalkInRegistrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkInRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnNext.setOnClickListener { validateAndSubmit() }
    }

    private fun validateAndSubmit() {
        val name = binding.etName.text.toString().trim()
        val surname = binding.etSurname.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val mobile = binding.etMobileNumber.text.toString().trim()
        val nationalId = binding.etNationalId.text.toString().trim()
        val street = binding.etStreet.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val houseNumber = binding.etHouseNumber.text.toString().trim()
        val suburb = binding.etSuburb.text.toString().trim()

        if (name.isEmpty() || surname.isEmpty() || mobile.isEmpty() || nationalId.isEmpty() || street.isEmpty() || city.isEmpty()) {
            Toast.makeText(this, getString(R.string.auto_rem_please_fill_in_all_required_fi), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnNext.isEnabled = false
        binding.btnNext.text = getString(R.string.auto_kt_registering)

        lifecycleScope.launch {
            val result = ZimpudoApp.apiRepository.partialRegisterUser(
                name = name,
                surname = surname,
                email = email.ifEmpty { "no-email@zimpudo.co.zw" },
                mobileNumber = mobile,
                nationalId = nationalId,
                houseNumber = houseNumber.ifEmpty { "0" },
                street = street,
                suburb = suburb.ifEmpty { city },
                city = city
            )

            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@WalkInRegistrationActivity, getString(R.string.auto_rem_registration_successful), Toast.LENGTH_SHORT).show()
                    // Save mobile in prefs to simulate "logged in" for the send package flow
                    ZimpudoApp.prefs.saveUserMobile(mobile)
                    // Move to Send Package activity
                    startActivity(Intent(this@WalkInRegistrationActivity, SendPackageActivity::class.java))
                    finish()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(this@WalkInRegistrationActivity, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                    binding.btnNext.isEnabled = true
                    binding.btnNext.text = getString(R.string.auto_kt_next)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }
}
