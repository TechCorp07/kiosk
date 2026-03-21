package com.blitztech.pudokiosk.ui.customer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.databinding.ActivityCustomerAccountBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.launch

/**
 * Customer Account Management Screen
 *
 * Displays profile details loaded from Prefs (name, mobile).
 * Allows the customer to change their PIN via:
 *   POST /api/v1/auth/change-pin  (changePin in ApiRepository)
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
    }

    private fun setupViews() {
        binding.tvName.text    = prefs.getUserName()    ?: "—"
        binding.tvMobile.text  = prefs.getUserMobile()  ?: "—"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finishSafely() }

        binding.btnChangePin.setOnClickListener {
            val oldPin = binding.etOldPin.text.toString().trim()
            val newPin = binding.etNewPin.text.toString().trim()
            val confirmPin = binding.etConfirmPin.text.toString().trim()

            if (!validate(oldPin, newPin, confirmPin)) return@setOnClickListener
            changePin(oldPin, newPin)
        }
    }

    private fun validate(old: String, new: String, confirm: String): Boolean {
        var ok = true
        if (old.length != 4) {
            binding.tilOldPin.error = "Current PIN must be 4 digits"
            ok = false
        } else binding.tilOldPin.error = null

        if (new.length != 4) {
            binding.tilNewPin.error = "New PIN must be 4 digits"
            ok = false
        } else binding.tilNewPin.error = null

        if (new != confirm) {
            binding.tilConfirmPin.error = "PINs do not match"
            ok = false
        } else binding.tilConfirmPin.error = null

        return ok
    }

    private fun changePin(oldPin: String, newPin: String) {
        setLoading(true)
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            if (token.isBlank()) {
                setLoading(false)
                showToast("Session expired. Please log in again.")
                return@launch
            }

            when (val result = api.changePin(oldPin, newPin, token)) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    showToast("PIN changed successfully!")
                    binding.etOldPin.text?.clear()
                    binding.etNewPin.text?.clear()
                    binding.etConfirmPin.text?.clear()
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    showToast("Failed to change PIN: ${result.message}")
                }
                is NetworkResult.Loading<*> -> { /* handled by setLoading */ }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnChangePin.isEnabled = !loading
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
