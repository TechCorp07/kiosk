package com.blitztech.pudokiosk.ui.sendpackage

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

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiRepository: ApiRepository
    private lateinit var sendPackageActivity: SendPackageActivity
    private lateinit var prefs: Prefs

    // Hardware drivers
    private lateinit var printerDriver: CustomTG2480HIIIDriver
    private lateinit var lockerController: LockerController
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
    }

    override fun onResume() {
        super.onResume()
        displayOrderSummary()
    }

    private fun setupDependencies() {
        apiRepository = ZimpudoApp.apiRepository
    }

    /**
     * Initialize printer and locker hardware
     */
    private fun initializeHardware() {
        try {
            printerDriver = CustomTG2480HIIIDriver(requireContext())
            lockerController = LockerController(requireContext())
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Hardware initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
     * Validate payment inputs
     */
    private fun validateInputs(): Boolean {
        val rawMobile = binding.etPaymentMobile.text.toString().trim()

        if (rawMobile.isEmpty()) {
            binding.tilPaymentMobile.error = "Mobile number is required"
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
            Toast.makeText(requireContext(), "Session expired. Please login again.", Toast.LENGTH_LONG).show()
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
                                "Payment initiated. Waiting for confirmation...",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            binding.progressBar.visibility = View.VISIBLE
                            binding.tvStatus.visibility = View.VISIBLE
                            binding.tvStatus.text = "Waiting for payment approval..."
                            
                            // Backend uses Paynow webhook to update order status.
                            // We poll the order to detect when it transitions to AWAITING_COURIER.
                            pollPaymentStatus(data.orderId, accessToken)
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

    private fun pollPaymentStatus(orderId: String, token: String) {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            var isApproved = false

            while (System.currentTimeMillis() - startTime < 120_000) {
                delay(5000)
                try {
                    val searchResult = apiRepository.searchOrder(orderId, token)
                    if (searchResult is NetworkResult.Success) {
                        val page = searchResult.data
                        val order = page.content.firstOrNull()
                        if (order != null && order.status == "AWAITING_COURIER") {
                            isApproved = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient network errors during polling
                }
            }

            if (isApproved) {
                Toast.makeText(requireContext(), "Payment successful!", Toast.LENGTH_SHORT).show()
                executePostPaymentWorkflow()
            } else {
                binding.progressBar.visibility = View.GONE
                binding.btnPay.isEnabled = true
                binding.tvStatus.text = "Payment confirmation timed out."
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Payment Timeout")
                    .setMessage("Payment confirmation timed out. If you were charged and no locker opened, please contact support with your tracking number: $orderId")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * Execute post-payment workflow:
     * 1. Print customer receipt
     * 2. Print barcode label
     * 3. Open assigned locker
     * 4. Monitor door — show success only after door confirmed closed
     */
    private fun executePostPaymentWorkflow() {
        binding.tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Initialize printer
                printerDriver.initialize()

                // Step 1: Print customer receipt
                binding.tvStatus.text = "🖨️ Printing Customer Receipt...\n(Please keep this for your records)"
                printCustomerReceipt()
                delay(3000)

                // Step 2: Print waybill label with Code128 barcode (BEFORE door opens, per spec)
                binding.tvStatus.text = "🖨️ Printing Waybill Label...\n(Please stick this label onto your package)"
                printBarcodeLabel()
                delay(3000)

                // Step 3: Security photo (fire-and-forget)
                SecurityCameraManager.getInstance(requireContext()).captureSecurityPhoto(
                    reason = PhotoReason.CLIENT_DEPOSIT,
                    referenceId = sendPackageActivity.sendPackageData.orderId,
                    userId = prefs.getUserMobile() ?: ""
                )

                // Step 4: Open locker
                val lockNumber = sendPackageActivity.sendPackageData.assignedLockNumber
                binding.tvStatus.text = "Opening locker $lockNumber..."
                openLocker()
                delay(500)

                // Step 5: Monitor door — wait for close
                // Receipt will be printed AFTER door closes (per spec Flow 2 Step 7)
                binding.tvStatus.text = "\uD83D\uDCE6 Locker $lockNumber is open.\nPlace your package inside, then close the door."
                startDoorMonitoring(lockNumber)

            } catch (e: Exception) {
                binding.tvStatus.text = "Error during post-payment workflow: ${e.message}"
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Print customer receipt
     */
    private suspend fun printCustomerReceipt() {
        try {
            val data = sendPackageActivity.sendPackageData
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            printerDriver.printText("CUSTOMER RECEIPT\n", fontSize = 2, bold = true, centered = true)
            printerDriver.printText("--------------------------------\n")
            printerDriver.printText("Date: $timestamp\n")
            printerDriver.printText("Order ID: ${data.orderId}\n")
            printerDriver.printText("Recipient: ${data.recipientName} ${data.recipientSurname}\n")
            printerDriver.printText("Mobile: ${data.recipientMobile}\n")
            printerDriver.printText("Size: ${data.packageSize?.displayName}\n")
            printerDriver.printText("Amount Paid: ${data.currency?.symbol}${String.format("%.2f", data.orderPrice)}\n")
            printerDriver.printText("Payment: ${data.paymentMethod?.displayName}\n")
            printerDriver.printText("--------------------------------\n")
            printerDriver.printText("Thank you for using ZimPudo!\n", bold = true, centered = true)
            printerDriver.printText("\n\n\n")
            printerDriver.feedAndCut()
        } catch (e: Exception) {
            Log.e("PaymentFragment", "Print receipt error: ${e.message}")
        }
    }

    /**
     * Print waybill label with Code128 barcode (per spec Section 11.1)
     * Uses tracking number / orderId as the barcode data
     */
    private suspend fun printBarcodeLabel() {
        try {
            val data = sendPackageActivity.sendPackageData
            
            printerDriver.printText("ZimPudo Waybill Label\n", fontSize = 2, bold = true, centered = true)
            printerDriver.printText("Order ID: ${data.orderId}\n")
            printerDriver.printText("To: ${data.recipientName} ${data.recipientSurname}\n")
            printerDriver.printText("Size: ${data.packageSize?.displayName ?: "-"}\n")
            printerDriver.printText("From: ${data.recipientCityName}\n")
            // Use Code128 barcode instead of QR (per spec: tracking number as Code128)
            printerDriver.printCode128(data.orderId)
            printerDriver.printText("\n\n")
            printerDriver.feedAndCut()
        } catch (e: Exception) {
            Log.e("PaymentFragment", "Print barcode error: ${e.message}")
        }
    }

    /**
     * Open the assigned locker
     */
    private suspend fun openLocker() {
        try {
            val lockNumber = sendPackageActivity.sendPackageData.assignedLockNumber
            
            // Connect to locker controller
            val connected = lockerController.connect()
            if (!connected) {
                throw Exception("Failed to connect to locker controller. Please check connections.")
            }

            // Unlock the locker
            val result = lockerController.unlockLock(lockNumber)

            if (result.status == com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol.LockStatus.COOLDOWN) {
                throw Exception("This locker was recently used. Please wait ~15 seconds and try again.")
            } else if (!result.success) {
                throw Exception("Failed to unlock locker: ${result.errorMessage}")
            }

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Locker error: ${e.message}. Please contact support.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Start monitoring the locker door.
     * Includes audio reminders per spec (10s warning, 60s timeout).
     */
    private fun startDoorMonitoring(lockNumber: Int) {
        val hw = com.blitztech.pudokiosk.deviceio.HardwareManager.getInstance(requireContext())
        activeDoorMonitor = DoorMonitor(lockerController, lockNumber, lifecycleScope)
        activeDoorMonitor?.start(
            onDoorOpenTooLong = {
                hw.speaker.startDoorCloseReminder()
            },
            onDoorTimeout = {
                hw.speaker.stopDoorCloseReminder()
                Toast.makeText(requireContext(), "Door timeout — please close the locker.", Toast.LENGTH_LONG).show()
            },
            onDoorClosed = {
                hw.speaker.stopDoorCloseReminder()
                lifecycleScope.launch {
                    // Per spec: print receipt AFTER door closes (Flow 2 Step 7)
                    printCustomerReceipt()
                    showSuccessDialog()
                }
            }
        )
    }

    /**
     * Show success dialog — triggered by door close event.
     * Also confirms the sender drop-off with the backend and disconnects the locker.
     */
    private fun showSuccessDialog() {
        binding.tvStatus.visibility = View.GONE
        activeDoorMonitor?.stop()
        activeDoorMonitor = null

        // Disconnect locker hardware now that the door is closed
        lifecycleScope.launch {
            try { lockerController.disconnect() } catch (_: Exception) {}
        }

        // Confirm sender drop-off with the backend (fire-and-forget)
        confirmSenderDropoff()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Package Sending Complete!")
            .setMessage(
                "Your package has been successfully processed.\n\n" +
                        "Receipt and barcode label have been printed.\n" +
                        "The locker is now secured.\n\n" +
                        "The recipient will be notified to collect."
            )
            .setPositiveButton("Done") { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Notify the backend that the sender has placed the package in the locker.
     * Uses multipart sender/dropoff: orderId, cellId, placeholder photo.
     * Best-effort — does not block UI.
     */
    private fun confirmSenderDropoff() {
        lifecycleScope.launch {
            try {
                val data = sendPackageActivity.sendPackageData
                val token = prefs.getAccessToken().orEmpty()
                if (token.isBlank() || data.orderId.isBlank()) return@launch

                // Get cellId — from verify-reservation response or track from assign
                val cellId = data.lockerId.ifBlank { prefs.getPrimaryLockerUuid() }

                val result = apiRepository.senderDropoff(
                    orderId = data.orderId,
                    cellId = cellId,
                    token = token
                )
                when (result) {
                    is NetworkResult.Success -> Log.d("PaymentFragment", "Sender dropoff confirmed")
                    is NetworkResult.Error -> Log.w("PaymentFragment", "Sender dropoff failed: ${result.message}")
                    is NetworkResult.Loading<*> -> { /* no-op */ }
                }
            } catch (e: Exception) {
                Log.w("PaymentFragment", "Sender dropoff error: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        activeDoorMonitor?.stop()
        activeDoorMonitor = null
        lifecycleScope.launch {
            try { lockerController.disconnect() } catch (_: Exception) {}
        }
        super.onDestroyView()
        _binding = null
    }
}