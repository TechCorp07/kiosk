package com.blitztech.pudokiosk.ui.sendpackage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.content.Context
import android.util.Log
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.order.PaymentMethod
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.FragmentPaymentBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
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
        val mobileNumber = binding.etPaymentMobile.text.toString().trim()

        if (mobileNumber.isEmpty()) {
            binding.tilPaymentMobile.error = "Mobile number is required"
            return false
        }

        if (!mobileNumber.startsWith("+263") || mobileNumber.length < 13) {
            binding.tilPaymentMobile.error = "Invalid mobile number"
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
        data.paymentMobileNumber = binding.etPaymentMobile.text.toString().trim()

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
                            data.transactionId = response.transactionId ?: ""
                            data.assignedLockNumber = response.lockNumber ?: 1

                            Toast.makeText(
                                requireContext(),
                                "Payment successful!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Execute post-payment workflow
                            executePostPaymentWorkflow()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Payment failed: ${response.message}",
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

                    is NetworkResult.Loading<*> -> { /* loading state handled by setLoading() */ }
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
                // Step 1: Print customer receipt
                binding.tvStatus.text = "Printing customer receipt..."
                printCustomerReceipt()
                delay(2000)

                // Step 2: Print barcode label
                binding.tvStatus.text = "Printing barcode label..."
                printBarcodeLabel()
                delay(2000)

                // Step 3: Security photo (fire-and-forget)
                SecurityCameraManager.getInstance(requireContext()).captureSecurityPhoto(
                    reason = PhotoReason.CLIENT_DEPOSIT,
                    referenceId = sendPackageActivity.sendPackageData.orderId,
                    userId = com.blitztech.pudokiosk.ZimpudoApp.prefs.getUserMobile() ?: ""
                )

                // Step 4: Open locker
                val lockNumber = sendPackageActivity.sendPackageData.assignedLockNumber
                binding.tvStatus.text = "Opening locker $lockNumber..."
                openLocker()
                delay(500)

                // Step 4: Monitor door — wait for close
                binding.tvStatus.text = "\uD83D\uDCE6 Locker $lockNumber is open.\nPlace your package inside, then close the door."
                startDoorMonitoring(lockNumber)

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Post-payment error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.tvStatus.visibility = View.GONE
            }
        }
    }

    /** Monitors the door sensor; shows success dialog once the door is confirmed closed. */
    private fun startDoorMonitoring(lockNumber: Int) {
        val hwManager = HardwareManager.getInstance(requireContext())
        val speaker = hwManager.speakerManager
        val monitor = hwManager.createDoorMonitor(
            lockerController = lockerController,
            lockNumber = lockNumber,
            coroutineScope = lifecycleScope
        )
        activeDoorMonitor = monitor
        monitor.start(
            onDoorOpenTooLong = {
                // Door open > 10 s — remind the customer
                speaker.startDoorCloseReminder()
                activity?.runOnUiThread {
                    binding.tvStatus.text = "⚠ Please close the locker door!"
                }
            },
            onDoorTimeout = {
                // Door open > 60 s — proceed anyway so customer isn't stranded
                speaker.stopDoorCloseReminder()
                activity?.runOnUiThread {
                    binding.tvStatus.text = "Timeout — please close the locker manually."
                    showSuccessDialog()
                }
            },
            onDoorClosed = {
                speaker.stopDoorCloseReminder()
                speaker.playSuccessChime()
                activity?.runOnUiThread {
                    showSuccessDialog()
                }
            }
        )
    }

    /**
     * Print customer receipt
     */
    private suspend fun printCustomerReceipt() {
        val data = sendPackageActivity.sendPackageData
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val receipt = buildString {
            appendLine("    ZIMPUDO KIOSK")
            appendLine("    SEND PACKAGE RECEIPT")
            appendLine("================================")
            appendLine()
            appendLine("Date: $currentDate")
            appendLine("Order ID: ${data.orderId}")
            appendLine("Transaction: ${data.transactionId}")
            appendLine()
            appendLine("SENDER INFORMATION")
            val senderName = prefs.getUserName()
            val senderMobile = prefs.getUserMobile()
            appendLine("Name: $senderName")
            appendLine("Mobile: $senderMobile")
            appendLine()
            appendLine("RECIPIENT INFORMATION")
            appendLine("Name: ${data.recipientName} ${data.recipientSurname}")
            appendLine("Mobile: ${data.recipientMobile}")
            appendLine("Address:")
            appendLine("  ${data.recipientHouseNumber} ${data.recipientStreet}")
            appendLine("  ${data.recipientSuburbName}")
            appendLine("  ${data.recipientCityName}")
            appendLine()
            appendLine("PACKAGE INFORMATION")
            appendLine("Size: ${data.packageSize?.displayName}")
            appendLine("Dimensions: ${data.packageLength}m x ${data.packageWidth}m x ${data.packageHeight}m")
            appendLine("Contents: ${data.packageContents}")
            appendLine()
            appendLine("PAYMENT INFORMATION")
            appendLine("Method: ${data.paymentMethod?.displayName}")
            appendLine("Amount: ${data.currency?.symbol}${String.format("%.2f", data.orderPrice)}")
            appendLine("Distance: ${data.orderDistance}")
            appendLine()
            appendLine("LOCKER INFORMATION")
            appendLine("Locker Number: ${data.assignedLockNumber}")
            appendLine()
            appendLine("Please place your package in")
            appendLine("Locker ${data.assignedLockNumber} and close the door.")
            appendLine()
            appendLine("================================")
            appendLine("Thank you for using ZIMPUDO!")
            appendLine("www.zimpudo.com")
            appendLine()
        }

        val result = printerDriver.printText(receipt, fontSize = 1, centered = false)

        if (result.isSuccess) {
            printerDriver.feedAndCut()
        } else {
            throw Exception("Receipt printing failed")
        }
    }

    /**
     * Print barcode label (to stick on package)
     */
    private suspend fun printBarcodeLabel() {
        val data = sendPackageActivity.sendPackageData

        // Print header
        printerDriver.printText("ZIMPUDO PACKAGE\n", fontSize = 2, bold = true, centered = true)
        printerDriver.printText("Order ID:\n", fontSize = 1, centered = true)

        // Print barcode (Order ID)
        val barcodeResult = printerDriver.printCode128(data.orderId, centered = true)

        if (!barcodeResult.isSuccess) {
            throw Exception("Barcode printing failed")
        }

        // Print order ID as text below barcode
        printerDriver.printText("\n${data.orderId}\n", fontSize = 1, centered = true)
        printerDriver.printText("Locker: ${data.assignedLockNumber}\n", fontSize = 2, bold = true, centered = true)
        printerDriver.printText("\nScan at delivery\n", fontSize = 1, centered = true)

        printerDriver.feedAndCut()
    }

    /**
     * Open assigned locker
     */
    private suspend fun openLocker() {
        val lockNumber = sendPackageActivity.sendPackageData.assignedLockNumber

        try {
            // Connect to locker controller
            val connected = lockerController.connect()

            if (!connected) {
                throw Exception("Failed to connect to locker controller")
            }

            // Unlock the locker
            val result = lockerController.unlockLock(lockNumber)

            if (!result.success) {
                throw Exception("Failed to unlock locker: ${result.errorMessage}")
            }

            // DO NOT disconnect here — DoorMonitor needs the connection
            // to poll the door sensor. Disconnect happens in showSuccessDialog()
            // or onDestroyView().

        } catch (e: Exception) {
            // Log error but don't fail the entire workflow
            Toast.makeText(
                requireContext(),
                "Locker error: ${e.message}. Please contact support.",
                Toast.LENGTH_LONG
            ).show()
        }
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
     * Uses the sender/dropoff transaction endpoint. Best-effort — does not block UI.
     */
    private fun confirmSenderDropoff() {
        lifecycleScope.launch {
            try {
                val data = sendPackageActivity.sendPackageData
                val token = prefs.getAccessToken().orEmpty()
                if (token.isBlank() || data.orderId.isBlank()) return@launch

                val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }
                val request = TransactionRequest(
                    trackingNumber = data.orderId,
                    kioskId = kioskId
                )
                val result = apiRepository.senderDropoff(request, token)
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
        kotlinx.coroutines.GlobalScope.launch {
            try { lockerController.disconnect() } catch (_: Exception) {}
        }
        super.onDestroyView()
        _binding = null
    }
}