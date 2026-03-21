package com.blitztech.pudokiosk.ui.sendpackage

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.order.Currency
import com.blitztech.pudokiosk.data.api.dto.order.PackageSize
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.FragmentPackageDetailsBinding
import com.blitztech.pudokiosk.prefs.Prefs
import kotlinx.coroutines.launch

/**
 * Fragment for entering package details
 */
class PackageDetailsFragment : Fragment() {

    private var _binding: FragmentPackageDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiRepository: ApiRepository
    private lateinit var sendPackageActivity: SendPackageActivity
    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPackageDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendPackageActivity = requireActivity() as SendPackageActivity
        prefs = Prefs(requireContext())
        setupDependencies()
        setupViews()
        setupClickListeners()
        restoreData()
    }

    private fun setupDependencies() {
        apiRepository = ZimpudoApp.apiRepository
    }

    private fun setupViews() {
        // Setup currency spinner
        val currencies = Currency.values().map { "${it.displayName} (${it.symbol})" }
        val currencyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, currencies)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCurrency.adapter = currencyAdapter

        // Add text watchers to calculate package size automatically
        val dimensionWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                calculatePackageSize()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etLength.addTextChangedListener(dimensionWatcher)
        binding.etWidth.addTextChangedListener(dimensionWatcher)
        binding.etHeight.addTextChangedListener(dimensionWatcher)

        binding.etContents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilContents.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener {
            if (validateInputs()) {
                saveData()
                createOrder()
            }
        }

        binding.btnBack.setOnClickListener {
            sendPackageActivity.goToPreviousPage()
        }
    }

    /**
     * Calculate package size based on dimensions
     */
    private fun calculatePackageSize() {
        try {
            val length = binding.etLength.text.toString().toDoubleOrNull() ?: 0.0
            val width = binding.etWidth.text.toString().toDoubleOrNull() ?: 0.0
            val height = binding.etHeight.text.toString().toDoubleOrNull() ?: 0.0

            if (length > 0 && width > 0 && height > 0) {
                val size = PackageSize.fromDimensions(length, width, height)
                binding.tvCalculatedSize.text = "Calculated Size: ${size.displayName}"
                binding.tvCalculatedSize.visibility = View.VISIBLE
            } else {
                binding.tvCalculatedSize.visibility = View.GONE
            }
        } catch (e: Exception) {
            binding.tvCalculatedSize.visibility = View.GONE
        }
    }

    /**
     * Validate all inputs
     */
    private fun validateInputs(): Boolean {
        var isValid = true

        // Length
        val length = binding.etLength.text.toString().toDoubleOrNull()
        if (length == null || length <= 0) {
            binding.tilLength.error = "Enter valid length in meters"
            isValid = false
        } else {
            binding.tilLength.error = null
        }

        // Width
        val width = binding.etWidth.text.toString().toDoubleOrNull()
        if (width == null || width <= 0) {
            binding.tilWidth.error = "Enter valid width in meters"
            isValid = false
        } else {
            binding.tilWidth.error = null
        }

        // Height
        val height = binding.etHeight.text.toString().toDoubleOrNull()
        if (height == null || height <= 0) {
            binding.tilHeight.error = "Enter valid height in meters"
            isValid = false
        } else {
            binding.tilHeight.error = null
        }

        // Contents
        val contents = binding.etContents.text.toString().trim()
        if (contents.isEmpty()) {
            binding.tilContents.error = "Package contents are required"
            isValid = false
        } else {
            binding.tilContents.error = null
        }

        return isValid
    }

    /**
     * Save data to shared state
     */
    private fun saveData() {
        val data = sendPackageActivity.sendPackageData

        data.packageLength = binding.etLength.text.toString().toDouble()
        data.packageWidth = binding.etWidth.text.toString().toDouble()
        data.packageHeight = binding.etHeight.text.toString().toDouble()
        data.packageContents = binding.etContents.text.toString().trim()
        data.packageSize = PackageSize.fromDimensions(
            data.packageLength,
            data.packageWidth,
            data.packageHeight
        )
        data.currency = Currency.values()[binding.spinnerCurrency.selectedItemPosition]
    }

    /**
     * Create order via API
     */
    private fun createOrder() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnNext.isEnabled = false

        val data = sendPackageActivity.sendPackageData
        val accessToken = prefs.getAccessToken()

        if (accessToken.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
            binding.btnNext.isEnabled = true
            return
        }

        lifecycleScope.launch {
            try {
                val result = apiRepository.createOrder(
                    packageDetails = data.buildPackageDetails(),
                    recipient = data.buildRecipient(),
                    senderLocation = data.buildSenderLocation(),
                    currency = data.currency!!.code,
                    token = accessToken
                )

                binding.progressBar.visibility = View.GONE
                binding.btnNext.isEnabled = true

                when (result) {
                    is NetworkResult.Success -> {
                        val response = result.data

                        // Save order details
                        data.orderId = response.orderId
                        data.orderPrice = response.price
                        data.orderDistance = response.distance

                        // Since we're on the locker, use current locker's ID
                        // In a real scenario, you'd get this from device/kiosk config
                        data.lockerId = response.nearestLockers.firstOrNull()?.lockerResponse?.id
                            ?: "current-locker-id"

                        Toast.makeText(
                            requireContext(),
                            "Order created: ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        sendPackageActivity.goToNextPage()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            requireContext(),
                            "Order creation failed: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is NetworkResult.Loading<*> -> { /* loading state handled by setLoading() */ }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnNext.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Restore previously entered data
     */
    private fun restoreData() {
        val data = sendPackageActivity.sendPackageData

        if (data.packageLength > 0) {
            binding.etLength.setText(data.packageLength.toString())
        }
        if (data.packageWidth > 0) {
            binding.etWidth.setText(data.packageWidth.toString())
        }
        if (data.packageHeight > 0) {
            binding.etHeight.setText(data.packageHeight.toString())
        }
        if (data.packageContents.isNotEmpty()) {
            binding.etContents.setText(data.packageContents)
        }
        if (data.currency != null) {
            binding.spinnerCurrency.setSelection(data.currency!!.ordinal)
        }

        calculatePackageSize()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}