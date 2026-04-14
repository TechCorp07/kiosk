package com.blitztech.pudokiosk.ui.sendpackage

import com.blitztech.pudokiosk.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.order.PaymentMethod
import com.blitztech.pudokiosk.data.api.dto.order.PaymentSearchPage
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.FragmentPaymentBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.utils.ValidationUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for payment processing
 * After successful payment: Print receipt → Print barcode → Open locker
 */
class PaymentFragment : Fragment() {

    companion object {
        private const val TAG = "PaymentFragment"
    }

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiRepository: ApiRepository
    private lateinit var sendPackageActivity: SendPackageActivity
    private lateinit var prefs: Prefs

    private var activeDoorMonitor: DoorMonitor? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendPackageActivity = requireActivity() as SendPackageActivity
        prefs = Prefs(requireContext())

        setupDependencies()
        initializeHardware()
        setupViews()
        setupClickListeners()
        // NOTE: displayOrderSummary() is called in onResume() to ensure
        // order data is available (ViewPager2 pre-creates adjacent fragments)
        
        if (sendPackageActivity.sendPackageData.isDropReserved) {
            handleDropReservedFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        displayOrderSummary()
    }

    private fun setupDependencies() {
        apiRepository = ZimpudoApp.apiRepository
    }

    /**
     * Initialize hardware (moved to ProcessingFragment)
     */
    private fun initializeHardware() {
        // No-op here, left for backwards compatibility in case it's still called somewhere
    }

    private fun setupViews() {
        // Setup payment method spinner
        val paymentMethods = PaymentMethod.values().map { it.displayName }
        val paymentAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, paymentMethods)
        paymentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPaymentMethod.adapter = paymentAdapter
    }

    private fun setupClickListeners() {
        binding.btnPay.setOnClickListener {
            if (validateInputs()) {
                processPayment()
            }
        }

        binding.btnBack.setOnClickListener {
            sendPackageActivity.goToPreviousPage()
        }
    }

    /**
     * Display order summary
     */
    private fun displayOrderSummary() {
        val data = sendPackageActivity.sendPackageData

        binding.tvOrderId.text = "Order ID: ${data.orderId}"
        binding.tvRecipient.text = "${data.recipientName} ${data.recipientSurname}"
        binding.tvPackageSize.text = "Size: ${data.packageSize?.displayName}"
        binding.tvDistance.text = "Distance: ${data.orderDistance}"
        binding.tvPrice.text = "${data.currency?.symbol}${String.format("%.2f", data.orderPrice)}"
        binding.tvCurrency.text = data.currency?.code ?: ""
    }

    /**
     * Skip payment UI if this is a Drop-off Reserved parcel
     */
    private fun handleDropReservedFlow() {
        binding.spinnerPaymentMethod.visibility = View.GONE
        binding.tilPaymentMobile.visibility = View.GONE
        binding.btnPay.visibility = View.GONE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.auto_kt_verifying_reservation)
        binding.progressBar.visibility = View.VISIBLE
        
        confirmReservationWithBackend()
    }
    
    private fun confirmReservationWithBackend() {
        val data = sendPackageActivity.sendPackageData
        val token = prefs.getAccessToken() ?: return
        
        lifecycleScope.launch {
            try {
                // Fetch order directly to confirm reservation state and get the locked cell number
                Log.i(TAG, "🔍 Verifying reservation for orderId=${data.orderId}")
                val searchResult = apiRepository.searchOrderById(data.orderId, token)
                if (searchResult is NetworkResult.Success) {
                    val order = searchResult.data.content.firstOrNull()
                    Log.i(TAG, "📦 Order found: id=${order?.orderId}, status=${order?.status}, cellId=${order?.cellId}, cellNumber=${order?.cellNumber}")
                    if (order != null) {
                        // Populate full data for receipts/labels
                        data.cellId = order.cellId ?: ""
                        data.assignedLockNumber = order.cellNumber ?: 0
                        data.orderPrice = order.price ?: 0.0
                        data.currency = com.blitztech.pudokiosk.data.api.dto.order.Currency.fromCode(order.currency ?: "USD")
                        data.recipientMobile = order.recipientId ?: ""
                        if (order.packageDetails?.packageSize != null) {
                            try {
                                data.packageSize = com.blitztech.pudokiosk.data.api.dto.order.PackageSize.valueOf(order.packageDetails.packageSize)
                            } catch (e: Exception) {}
                        }

                        // Step 1: If backend gave us a cellId but no physical door number, resolve from local DB
                        if (data.assignedLockNumber == 0 && data.cellId.isNotBlank()) {
                            Log.i(TAG, "🔎 Backend provided cellId=${data.cellId} but no cellNumber, resolving from local DB...")
                            val localCell = ZimpudoApp.database.cells().getCellByUuid(data.cellId)
                            if (localCell != null) {
                                data.assignedLockNumber = localCell.physicalDoorNumber
                                Log.i(TAG, "✅ Resolved cellId=${data.cellId} to physical door ${data.assignedLockNumber}")
                            } else {
                                Log.w(TAG, "⚠️ cellId=${data.cellId} not found in local cells DB")
                            }
                        }

                        // Step 2: If still no cell assigned (backend didn't assign one at reservation time),
                        // allocate the next available cell from local inventory
                        if (data.assignedLockNumber == 0) {
                            Log.i(TAG, "📭 No cell pre-assigned by backend, allocating from local inventory...")
                            val lockerUuid = prefs.getPrimaryLockerUuid()
                            val availableCell = ZimpudoApp.database.cells().getNextAvailableCell(lockerUuid)
                            if (availableCell != null) {
                                data.cellId = availableCell.cellUuid
                                data.assignedLockNumber = availableCell.physicalDoorNumber
                                ZimpudoApp.database.cells().markCellOccupied(availableCell.cellUuid)
                                Log.i(TAG, "✅ Locally assigned cellId=${data.cellId} door=${data.assignedLockNumber} from locker $lockerUuid")
                            } else {
                                // Check how many cells are in local DB at all
                                val totalCells = ZimpudoApp.database.cells().getAllForLocker(lockerUuid)
                                Log.e(TAG, "❌ No available cells in local DB for locker $lockerUuid (total cells in DB: ${totalCells.size})")
                                totalCells.forEach { c ->
                                    Log.d(TAG, "  Cell: uuid=${c.cellUuid}, door=${c.physicalDoorNumber}, status=${c.status}")
                                }
                            }
                        }
                        
                        if (data.assignedLockNumber > 0) {
                            Log.i(TAG, "🚀 Proceeding to ProcessingFragment with door=${data.assignedLockNumber}, cellId=${data.cellId}")
                            binding.progressBar.visibility = View.GONE
                            sendPackageActivity.goToNextPage() // Proceed to ProcessingFragment
                            return@launch
                        }
                    }
                    
                    Log.e(TAG, "❌ Failed to locate locker cell for reserved order (orderId=${data.orderId}, cellId=${data.cellId}, lockNumber=${data.assignedLockNumber})")
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = getString(R.string.auto_kt_failed_to_locate_locker_cell_p)
                } else {
                    binding.progressBar.visibility = View.GONE
                    val errorMsg = (searchResult as? com.blitztech.pudokiosk.data.api.NetworkResult.Error)?.message ?: "Unknown error"
                    Log.e(TAG, "❌ Reservation search failed: $errorMsg")
                    binding.tvStatus.text = "Reservation verification failed: $errorMsg"
                    Toast.makeText(requireContext(), "Verification failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during reservation verification", e)
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.auto_kt_system_error_during_verificati)
            }
        }
    }

    /**
     * Validate payment inputs
     */
    private fun validateInputs(): Boolean {
        val rawMobile = binding.etPaymentMobile.text.toString().trim()

        if (rawMobile.isEmpty()) {
            binding.tilPaymentMobile.error = getString(R.string.auto_rem_mobile_number_is_required)
            return false
        }

        val normalized = ValidationUtils.formatPhoneNumber(rawMobile)
        if (!ValidationUtils.isValidPhoneNumber(normalized)) {
            binding.tilPaymentMobile.error = ValidationUtils.getPhoneErrorMessage()
            return false
        }

        binding.tilPaymentMobile.error = null
        return true
    }

    /**
     * Process payment via API
     */
    private fun processPayment() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPay.isEnabled = false

        val data = sendPackageActivity.sendPackageData
        val accessToken = prefs.getAccessToken()

        if (accessToken.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.auto_rem_session_expired_please_login_a), Toast.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
            binding.btnPay.isEnabled = true
            return
        }

        // Save payment details
        data.paymentMethod = PaymentMethod.values()[binding.spinnerPaymentMethod.selectedItemPosition]
        data.paymentMobileNumber = ValidationUtils.formatPhoneNumber(binding.etPaymentMobile.text.toString().trim())

        lifecycleScope.launch {
            try {
                val result = apiRepository.createPayment(
                    orderId = data.orderId,
                    lockerId = data.lockerId,
                    paymentMethod = data.paymentMethod!!.apiValue,
                    mobileNumber = data.paymentMobileNumber,
                    currency = data.currency!!.code,
                    token = accessToken
                )

                binding.progressBar.visibility = View.GONE
                binding.btnPay.isEnabled = true

                when (result) {
                    is NetworkResult.Success -> {
                        val response = result.data

                        if (response.success) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.auto_rem_payment_initiated_waiting_for_),
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            binding.progressBar.visibility = View.VISIBLE
                            binding.tvStatus.visibility = View.VISIBLE
                            binding.tvStatus.text = getString(R.string.auto_kt_waiting_for_payment_approval)
                            
                            // Backend uses Paynow webhook to update order status.
                            // We proceed to ProcessingFragment which will poll the status.
                            sendPackageActivity.goToNextPage()
                        } else {
                            val errorMsg = response.errors?.values?.firstOrNull() ?: response.message
                            Toast.makeText(
                                requireContext(),
                                "Payment failed: $errorMsg",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            requireContext(),
                            "Payment error: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is NetworkResult.Loading<*> -> { /* no-op */ }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnPay.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}