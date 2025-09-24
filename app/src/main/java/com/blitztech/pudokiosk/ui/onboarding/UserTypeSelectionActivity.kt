package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityUserTypeSelectionBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.auth.SignInActivity

class UserTypeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserTypeSelectionBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserTypeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ZimpudoApp.prefs

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        // Set localized text
        binding.tvTitle.text = getString(R.string.select_user_type)
        binding.tvSubtitle.text = getString(R.string.user_type_subtitle)

        // Customer card
        binding.tvCustomerTitle.text = getString(R.string.user_type_customer)
        binding.tvCustomerDescription.text = getString(R.string.user_type_customer_desc)

        // Courier card
        binding.tvCourierTitle.text = getString(R.string.user_type_courier)
        binding.tvCourierDescription.text = getString(R.string.user_type_courier_desc)
    }

    private fun setupClickListeners() {
        binding.cardCustomer.setOnClickListener {
            navigateToAuth(UserType.CUSTOMER)
        }

        binding.cardCourier.setOnClickListener {
            navigateToAuth(UserType.COURIER)
        }
    }

    private fun navigateToAuth(userType: UserType) {
        val intent = Intent(this, SignInActivity::class.java).apply {
            putExtra(SignInActivity.EXTRA_USER_TYPE, userType.name)
        }
        startActivity(intent)
        finish()
    }
}