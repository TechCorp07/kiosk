package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.databinding.ActivityUserTypeSelectionBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.auth.SignInActivity

class UserTypeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserTypeSelectionBinding
    private lateinit var prefs: Prefs
    private lateinit var i18n: I18n

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserTypeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        i18n = I18n(this)

        // Load current language
        val currentLocale = prefs.getLocale()
        i18n.load(currentLocale)

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        // Set localized text
        binding.tvTitle.text = i18n.t("select_user_type", "Select User Type")
        binding.tvSubtitle.text = i18n.t("user_type_subtitle", "Choose your role to continue")

        // Customer card
        binding.tvCustomerTitle.text = i18n.t("user_type_customer", "Customer")
        binding.tvCustomerDescription.text = i18n.t("user_type_customer_desc", "Send or collect packages")

        // Courier card
        binding.tvCourierTitle.text = i18n.t("user_type_courier", "Courier")
        binding.tvCourierDescription.text = i18n.t("user_type_courier_desc", "Deliver packages to customers")
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