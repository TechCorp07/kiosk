package com.blitztech.pudokiosk.ui.technician

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
        binding.tvTitle.text = "Technician Access"
        binding.tvSubtitle.text = "Enter technician credentials to access diagnostic tools"
        binding.etUsername.hint = "Username"
        binding.etPassword.hint = "Password"
        binding.btnLogin.text = "Access Diagnostics"
        binding.btnCancel.text = "Cancel"
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            attemptTechnicianLogin()
        }

        binding.btnCancel.setOnClickListener {
            returnToMainApp()
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
            Toast.makeText(this, "Access granted", Toast.LENGTH_SHORT).show()
            navigateToTechMenu()
        } else {
            showError("Invalid credentials")
            clearFields()
        }
    }

    private fun navigateToTechMenu() {
        val intent = Intent(this, TechnicianMenuActivity::class.java)
        startActivity(intent)
        finish()
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