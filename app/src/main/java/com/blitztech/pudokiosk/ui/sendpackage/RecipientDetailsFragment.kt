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
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.location.CityDto
import com.blitztech.pudokiosk.data.api.dto.location.SuburbDto
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.FragmentRecipientDetailsBinding
import com.blitztech.pudokiosk.utils.ValidationUtils
import kotlinx.coroutines.launch

/**
 * Fragment for entering recipient details
 */
class RecipientDetailsFragment : Fragment() {

    private var _binding: FragmentRecipientDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiRepository: ApiRepository
    private lateinit var sendPackageActivity: SendPackageActivity

    private val allCities = mutableListOf<CityDto>()
    private val allSuburbs = mutableListOf<SuburbDto>()
    private val filteredSuburbs = mutableListOf<SuburbDto>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipientDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendPackageActivity = requireActivity() as SendPackageActivity
        setupDependencies()
        setupViews()
        setupClickListeners()
        loadCitiesAndSuburbs()
        restoreData()
    }

    private fun setupDependencies() {
        apiRepository = ZimpudoApp.apiRepository
    }

    private fun setupViews() {
        // Setup city dropdown
        binding.spinnerCity.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (allCities.isNotEmpty() && position > 0) { // Skip "Select City" hint
                    val selectedCity = allCities[position - 1]
                    onCitySelected(selectedCity)
                } else {
                    clearSuburbs()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                clearSuburbs()
            }
        })

        // Add text watchers for validation
        binding.etRecipientName.addTextChangedListener(createTextWatcher { binding.tilRecipientName.error = null })
        binding.etRecipientSurname.addTextChangedListener(createTextWatcher { binding.tilRecipientSurname.error = null })
        binding.etRecipientMobile.addTextChangedListener(createTextWatcher { binding.tilRecipientMobile.error = null })
        binding.etRecipientNationalId.addTextChangedListener(createTextWatcher { binding.tilRecipientNationalId.error = null })
        binding.etHouseNumber.addTextChangedListener(createTextWatcher { binding.tilHouseNumber.error = null })
        binding.etStreet.addTextChangedListener(createTextWatcher { binding.tilStreet.error = null })
    }

    private fun createTextWatcher(onTextChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
    }

    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener {
            if (validateInputs()) {
                saveData()
                sendPackageActivity.goToNextPage()
            }
        }

        binding.btnBack.setOnClickListener {
            sendPackageActivity.goToPreviousPage()
        }
    }

    /**
     * Load cities and suburbs from backend
     */
    private fun loadCitiesAndSuburbs() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Load cities
                when (val citiesResult = apiRepository.getCities()) {
                    is NetworkResult.Success -> {
                        allCities.clear()
                        allCities.addAll(citiesResult.data)
                        setupCitySpinner()

                        // Load all suburbs
                        loadAllSuburbs()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load cities: ${citiesResult.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.progressBar.visibility = View.GONE
                        binding.scrollView.visibility = View.VISIBLE
                    }

                    is NetworkResult.Loading<*> -> { /* loading state handled via progressBar */ }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                binding.scrollView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Load all suburbs for all cities
     */
    private suspend fun loadAllSuburbs() {
        allSuburbs.clear()

        for (city in allCities) {
            when (val suburbsResult = apiRepository.getSuburbs(city.id)) {
                is NetworkResult.Success -> {
                    allSuburbs.addAll(suburbsResult.data)
                }
                is NetworkResult.Error -> {
                    // Continue loading other suburbs even if one fails
                }

                is NetworkResult.Loading<*> -> { /* loading state handled by setLoading() */ }
            }
        }

        binding.progressBar.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE
    }

    /**
     * Setup city spinner
     */
    private fun setupCitySpinner() {
        val cityNames = listOf("Select City") + allCities.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cityNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCity.adapter = adapter
    }

    /**
     * Handle city selection
     */
    private fun onCitySelected(city: CityDto) {
        filteredSuburbs.clear()
        filteredSuburbs.addAll(allSuburbs.filter { it.city.id == city.id })
        setupSuburbSpinner()
    }

    /**
     * Setup suburb spinner
     */
    private fun setupSuburbSpinner() {
        val suburbNames = if (filteredSuburbs.isEmpty()) {
            listOf("No suburbs available")
        } else {
            listOf("Select Suburb") + filteredSuburbs.map { it.name }
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, suburbNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSuburb.adapter = adapter
        binding.spinnerSuburb.isEnabled = filteredSuburbs.isNotEmpty()
    }

    /**
     * Clear suburbs
     */
    private fun clearSuburbs() {
        filteredSuburbs.clear()
        binding.spinnerSuburb.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Select city first")
        )
        binding.spinnerSuburb.isEnabled = false
    }

    /**
     * Validate all inputs
     */
    private fun validateInputs(): Boolean {
        var isValid = true

        // Name
        val name = binding.etRecipientName.text.toString().trim()
        if (name.isEmpty()) {
            binding.tilRecipientName.error = "Name is required"
            isValid = false
        }

        // Surname
        val surname = binding.etRecipientSurname.text.toString().trim()
        if (surname.isEmpty()) {
            binding.tilRecipientSurname.error = "Surname is required"
            isValid = false
        }

        // Mobile — normalize first, then validate
        val mobile = ValidationUtils.formatPhoneNumber(binding.etRecipientMobile.text.toString().trim())
        if (!ValidationUtils.isValidPhoneNumber(mobile)) {
            binding.tilRecipientMobile.error = ValidationUtils.getPhoneErrorMessage()
            isValid = false
        }

        // National ID — normalize first, then validate
        val nationalId = ValidationUtils.formatNationalId(binding.etRecipientNationalId.text.toString().trim())
        if (!ValidationUtils.isValidNationalId(nationalId)) {
            binding.tilRecipientNationalId.error = ValidationUtils.getNationalIdErrorMessage()
            isValid = false
        }

        // House Number
        val houseNumber = binding.etHouseNumber.text.toString().trim()
        if (houseNumber.isEmpty()) {
            binding.tilHouseNumber.error = "House number is required"
            isValid = false
        }

        // Street
        val street = binding.etStreet.text.toString().trim()
        if (street.isEmpty()) {
            binding.tilStreet.error = "Street is required"
            isValid = false
        }

        // City
        if (binding.spinnerCity.selectedItemPosition == 0) {
            Toast.makeText(requireContext(), "Please select a city", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // Suburb
        if (binding.spinnerSuburb.selectedItemPosition == 0 || filteredSuburbs.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a suburb", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    /**
     * Save data to shared state
     */
    private fun saveData() {
        val data = sendPackageActivity.sendPackageData

        data.recipientName = binding.etRecipientName.text.toString().trim()
        data.recipientSurname = binding.etRecipientSurname.text.toString().trim()
        // Save in normalized format (+263... and XX-XXXXXX-X-XX)
        data.recipientMobile = ValidationUtils.formatPhoneNumber(binding.etRecipientMobile.text.toString().trim())
        data.recipientNationalId = ValidationUtils.formatNationalId(binding.etRecipientNationalId.text.toString().trim())
        data.recipientHouseNumber = binding.etHouseNumber.text.toString().trim()
        data.recipientStreet = binding.etStreet.text.toString().trim()

        val cityPosition = binding.spinnerCity.selectedItemPosition
        if (cityPosition > 0) {
            val selectedCity = allCities[cityPosition - 1]
            data.recipientCityId = selectedCity.id
            data.recipientCityName = selectedCity.name
        }

        val suburbPosition = binding.spinnerSuburb.selectedItemPosition
        if (suburbPosition > 0 && filteredSuburbs.isNotEmpty()) {
            val selectedSuburb = filteredSuburbs[suburbPosition - 1]
            data.recipientSuburbId = selectedSuburb.id
            data.recipientSuburbName = selectedSuburb.name
        }
    }

    /**
     * Restore previously entered data
     */
    private fun restoreData() {
        val data = sendPackageActivity.sendPackageData

        binding.etRecipientName.setText(data.recipientName)
        binding.etRecipientSurname.setText(data.recipientSurname)
        binding.etRecipientMobile.setText(data.recipientMobile)
        binding.etRecipientNationalId.setText(data.recipientNationalId)
        binding.etHouseNumber.setText(data.recipientHouseNumber)
        binding.etStreet.setText(data.recipientStreet)

        // Restore city and suburb selections after data is loaded
        lifecycleScope.launch {
            // Wait for cities to load
            while (allCities.isEmpty()) {
                kotlinx.coroutines.delay(100)
            }

            if (data.recipientCityId.isNotEmpty()) {
                val cityIndex = allCities.indexOfFirst { it.id == data.recipientCityId }
                if (cityIndex >= 0) {
                    binding.spinnerCity.setSelection(cityIndex + 1)

                    // Wait for suburbs to filter
                    kotlinx.coroutines.delay(100)

                    if (data.recipientSuburbId.isNotEmpty()) {
                        val suburbIndex = filteredSuburbs.indexOfFirst { it.id == data.recipientSuburbId }
                        if (suburbIndex >= 0) {
                            binding.spinnerSuburb.setSelection(suburbIndex + 1)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}