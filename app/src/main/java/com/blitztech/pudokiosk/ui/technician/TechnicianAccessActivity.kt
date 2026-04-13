package com.blitztech.pudokiosk.ui.technician

import com.blitztech.pudokiosk.R

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.blitztech.pudokiosk.databinding.ActivityTechnicianAccessBinding
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Technician access screen for kiosk maintenance and diagnostics
 * Hardcoded credentials for technical personnel
 */
class TechnicianAccessActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityTechnicianAccessBinding

    // Hardcoded technician credentials
    // TODO [POST-LAUNCH BACKEND]: Replace hardcoded passcode with daily TOTP
    // Endpoint: GET /api/v1/kiosks/daily-code?deviceId=
    private val validCredentials = mapOf(
        "tech001" to "admin123",
        "service" to "service2024",
        "maintenance" to "maintain456"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTechnicianAccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        binding.tvTitle.text = getString(R.string.auto_kt_technician_access)
        binding.tvSubtitle.text = getString(R.string.auto_kt_enter_technician_credentials_t)
        binding.etUsername.hint = getString(R.string.auto_rem_username)
        binding.etPassword.hint = getString(R.string.auto_rem_password)
        binding.btnLogin.text = getString(R.string.auto_kt_login)
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            attemptTechnicianLogin()
        }

        binding.btnBack.setOnClickListener {
            returnToMainApp()
        }
    }

    private fun attemptTechnicianLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password")
            return
        }

        if (validCredentials[username] == password) {
            Toast.makeText(this, getString(R.string.auto_rem_access_granted), Toast.LENGTH_SHORT).show()
            navigateToTechMenu()
        } else {
            showError("Invalid credentials")
            clearFields()
        }
    }

    private fun navigateToTechMenu() {
        disableKioskMode()
        val intent = Intent(this, TechnicianMenuActivity::class.java)
        startActivity(intent)
        finishSafely()
    }

    private fun returnToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun clearFields() {
        binding.etUsername.text?.clear()
        binding.etPassword.text?.clear()
        binding.etUsername.requestFocus()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}