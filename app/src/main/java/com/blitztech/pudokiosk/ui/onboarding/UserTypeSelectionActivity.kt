package com.blitztech.pudokiosk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityUserTypeSelectionBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.auth.SignInActivity
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.collect.CollectionCodeActivity
import com.blitztech.pudokiosk.ui.courier.CourierLoginActivity

class UserTypeSelectionActivity : BaseKioskActivity() {

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
        //binding.tvTitle.text = getString(R.string.select_user_type)

        // Customer Receive card
        binding.tvCustomerReceiveTitle.text = getString(R.string.user_type_customer_receive)
        binding.tvCustomerReceiveDesc.text = getString(R.string.user_type_customer_receive_desc)

        // Customer Send card
        binding.tvCustomerSendTitle.text = getString(R.string.user_type_customer_send)
        binding.tvCustomerSendDesc.text = getString(R.string.user_type_customer_send_desc)

        // Courier card
        binding.tvCourierTitle.text = getString(R.string.user_type_courier)
        binding.tvCourierDescription.text = getString(R.string.user_type_courier_desc)
    }

    private fun setupClickListeners() {
        // Customer Receive → go directly to collection flow (no login required)
        binding.cardCustomerReceive.setOnClickListener {
            startActivity(Intent(this, CollectionCodeActivity::class.java))
            finish()
        }

        // Customer Send → sign in as CUSTOMER (send flow)
        binding.cardCustomerSend.setOnClickListener {
            navigateToAuth(UserType.CUSTOMER)
        }

        // Courier → PIN-based courier login
        binding.cardCourier.setOnClickListener {
            startActivity(Intent(this, CourierLoginActivity::class.java))
            finish()
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