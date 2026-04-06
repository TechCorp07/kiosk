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

    /** Content types fetched from backend GET /api/v1/orders/packages */
    private var contentTypes: List<String> = emptyList()

    /**
     * Static list of package class enum values matching the backend's PackageClass enum.
     * These are sent as part of the POST /api/v1/orders payload, not fetched from a separate endpoint.
     */
    private data class PackageClassOption(val enumValue: String, val displayName: String)
    private val packageClassOptions = listOf(
        PackageClassOption("STANDARD", "Standard"),
        PackageClassOption("FRAGILE", "Fragile ⚠️"),
        PackageClassOption("EXPRESS", "Express ⚡"),
        PackageClassOption("PERISHABLE", "Perishable 🧊")
    )

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
        loadPackageContentTypes()
        restoreData()
        setupPackageClassSpinner()
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
    }

    /**
     * Load package content types from backend API.
     * Falls back to a hardcoded list if the API call fails.
     */
    private fun loadPackageContentTypes() {
        lifecycleScope.launch {
            when (val result = apiRepository.getPackageContentTypes()) {
                is NetworkResult.Success -> {
                    // Filter to only show PERMITTED types (backend returns all)
                    contentTypes = result.data
                    setupContentsSpinner(contentTypes)
                }
                is NetworkResult.Error -> {
                    // Fallback to static list if API fails
                    contentTypes = listOf(
                        "Documents", "Clothing", "Electronics", "Books",
                        "Food (Non-Perishable)", "Cosmetics", "Medicine (OTC)",
                        "Household Items", "Tools & Hardware", "Toys & Games",
                        "Gifts & Accessories", "Stationery", "Footwear",
                        "Phone Accessories", "Computer Accessories"
                    )
                    setupContentsSpinner(contentTypes)
                }
                is NetworkResult.Loading -> { /* handled by spinner state */ }
            }
        }
    }

    private fun setupContentsSpinner(items: List<String>) {
        val displayItems = listOf("Select Package Contents") + items
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerContents.adapter = adapter

        // Restore selection if data exists
        val data = sendPackageActivity.sendPackageData
        if (data.packageContents.isNotBlank()) {
            // Try to find the content in the dropdown, strip any additional details
            val contentCategory = data.packageContents.split(" - ").firstOrNull() ?: data.packageContents
            val index = items.indexOf(contentCategory)
            if (index >= 0) {
                binding.spinnerContents.setSelection(index + 1) // +1 for "Select" header
            }
        }
    }

    private fun setupPackageClassSpinner() {
        val classNames = packageClassOptions.map { it.displayName }
        val classAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPackageClass.adapter = classAdapter

        // Restore previous selection if any
        val data = sendPackageActivity.sendPackageData
        if (data.packageClass.isNotBlank()) {
            val selectedIndex = packageClassOptions.indexOfFirst { it.enumValue == data.packageClass }
            if (selectedIndex >= 0) {
                binding.spinnerPackageClass.setSelection(selectedIndex)
            }
        }
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
            val length = (binding.etLength.text.toString().toDoubleOrNull() ?: 0.0) / 1000.0
            val width = (binding.etWidth.text.toString().toDoubleOrNull() ?: 0.0) / 1000.0
            val height = (binding.etHeight.text.toString().toDoubleOrNull() ?: 0.0) / 1000.0

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
            binding.tilLength.error = "Enter valid length in mm"
            isValid = false
        } else {
            binding.tilLength.error = null
        }

        // Width
        val width = binding.etWidth.text.toString().toDoubleOrNull()
        if (width == null || width <= 0) {
            binding.tilWidth.error = "Enter valid width in mm"
            isValid = false
        } else {
            binding.tilWidth.error = null
        }

        // Height
        val height = binding.etHeight.text.toString().toDoubleOrNull()
        if (height == null || height <= 0) {
            binding.tilHeight.error = "Enter valid height in mm"
            isValid = false
        } else {
            binding.tilHeight.error = null
        }

        // Contents - spinner selection required
        if (binding.spinnerContents.selectedItemPosition == 0) {
            Toast.makeText(requireContext(), "Please select package contents", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    /**
     * Save data to shared state
     */
    private fun saveData() {
        val data = sendPackageActivity.sendPackageData

        data.packageLength = (binding.etLength.text.toString().toDoubleOrNull() ?: 0.0) / 1000.0
        data.packageWidth = (binding.etWidth.text.toString().toDoubleOrNull() ?: 0.0) / 1000.0
        data.packageHeight = (binding.etHeight.text.toString().toDoubleOrNull() ?: 0.0) / 1000.0

        // Build contents string: category + optional details
        val selectedContentPosition = binding.spinnerContents.selectedItemPosition
        val contentCategory = if (selectedContentPosition > 0 && contentTypes.isNotEmpty()) {
            contentTypes[selectedContentPosition - 1]
        } else {
            "Other"
        }
        val additionalDetails = binding.etContents.text.toString().trim()
        data.packageContents = if (additionalDetails.isNotBlank()) {
            "$contentCategory - $additionalDetails"
        } else {
            contentCategory
        }

        data.packageSize = PackageSize.fromDimensions(
            data.packageLength,
            data.packageWidth,
            data.packageHeight
        )
        data.currency = Currency.values()[binding.spinnerCurrency.selectedItemPosition]
        
        val selectedPos = binding.spinnerPackageClass.selectedItemPosition
        if (selectedPos >= 0 && selectedPos < packageClassOptions.size) {
            val selectedClass = packageClassOptions[selectedPos]
            data.packageClass = selectedClass.enumValue
            data.packageClassName = selectedClass.displayName
        }
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

    private fun formatMm(meters: Double): String {
        val mm = meters * 1000.0
        return if (mm % 1.0 == 0.0) mm.toInt().toString() else mm.toString()
    }

    /**
     * Restore previously entered data
     */
    private fun restoreData() {
        val data = sendPackageActivity.sendPackageData

        if (data.packageLength > 0) {
            binding.etLength.setText(formatMm(data.packageLength))
        }
        if (data.packageWidth > 0) {
            binding.etWidth.setText(formatMm(data.packageWidth))
        }
        if (data.packageHeight > 0) {
            binding.etHeight.setText(formatMm(data.packageHeight))
        }
        if (data.packageContents.isNotEmpty()) {
            // Restore additional details part only
            val parts = data.packageContents.split(" - ", limit = 2)
            if (parts.size > 1) {
                binding.etContents.setText(parts[1])
            }
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