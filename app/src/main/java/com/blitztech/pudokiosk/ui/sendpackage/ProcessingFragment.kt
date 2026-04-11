package com.blitztech.pudokiosk.ui.sendpackage

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.order.PaymentSearchPage
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.FragmentProcessingBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol
import com.blitztech.pudokiosk.prefs.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProcessingFragment : Fragment() {

    private var _binding: FragmentProcessingBinding? = null
    private val binding get() = _binding!!

    private lateinit var apiRepository: ApiRepository
    private lateinit var sendPackageActivity: SendPackageActivity
    private lateinit var prefs: Prefs

    private lateinit var printerDriver: CustomTG2480HIIIDriver
    private lateinit var lockerController: LockerController
    private var activeDoorMonitor: DoorMonitor? = null

    private var hasStartedProcessing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sendPackageActivity = requireActivity() as SendPackageActivity
        prefs = Prefs(requireContext())
        apiRepository = ZimpudoApp.apiRepository

        try {
            printerDriver = CustomTG2480HIIIDriver(requireContext())
            lockerController = LockerController(requireContext())
        } catch (e: Exception) {
            Log.e("ProcessingFragment", "Hardware init error: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasStartedProcessing) {
            hasStartedProcessing = true
            startProcessing()
        }
    }

    private fun startProcessing() {
        val data = sendPackageActivity.sendPackageData
        if (data.isDropReserved) {
            // Bypass payment step
            setStepState(1, StepState.DONE, "Payment already completed")
            startHardwareWorkflow()
        } else {
            // Poll for payment
            setStepState(1, StepState.IN_PROGRESS, "Waiting for payment approval...")
            pollPaymentStatus(data.orderId, prefs.getAccessToken() ?: "")
        }
    }

    private fun pollPaymentStatus(orderId: String, token: String) {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            var isApproved = false
            var assignedCellNumber = 0
            var assignedCellId = ""

            // 120 second timeout
            while (System.currentTimeMillis() - startTime < 120_000) {
                delay(5000)
                try {
                    val searchResult = apiRepository.searchOrderById(orderId, token)
                    if (searchResult is NetworkResult.Success) {
                        val order = searchResult.data.content.firstOrNull()
                        if (order != null && (order.status == "AWAITING_COURIER" || order.status == "PAID")) {
                            assignedCellId = order.cellId ?: ""
                            if (order.cellNumber != null && order.cellNumber > 0) {
                                assignedCellNumber = order.cellNumber
                            }
                            isApproved = true
                            break
                        }
                    }

                    if (!isApproved) {
                        val payResult = apiRepository.searchPaymentByOrderId(orderId, token)
                        if (payResult is NetworkResult.Success<PaymentSearchPage>) {
                            val payment = payResult.data.content.firstOrNull()
                            if (payment != null && payment.paymentStatus == "PAID") {
                                isApproved = true
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient network errors
                }
            }

            if (isApproved) {
                val data = sendPackageActivity.sendPackageData
                
                // If it's a walk-in, the backend might not have assigned a cellId yet.
                // We must query the local Room DB to find an available cell and assign it.
                if (assignedCellId.isBlank() || assignedCellNumber == 0) {
                    try {
                        val lockerUuid = prefs.getPrimaryLockerUuid()
                        val localCell = com.blitztech.pudokiosk.ZimpudoApp.database.cells().getNextAvailableCell(lockerUuid)
                        if (localCell != null) {
                            assignedCellId = localCell.cellUuid
                            assignedCellNumber = localCell.physicalDoorNumber
                            // Mark cell as occupied locally to prevent double assignment
                            com.blitztech.pudokiosk.ZimpudoApp.database.cells().markCellOccupied(localCell.cellUuid)
                            android.util.Log.i("ProcessingFragment", "Walk-in locally assigned cellId: $assignedCellId (door $assignedCellNumber)")
                        } else {
                            android.util.Log.e("ProcessingFragment", "No available cells found in local DB!")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProcessingFragment", "Error fetching local cell", e)
                    }
                }

                if (assignedCellId.isNotBlank()) data.cellId = assignedCellId
                if (assignedCellNumber > 0) data.assignedLockNumber = assignedCellNumber

                setStepState(1, StepState.DONE, "Payment confirmed!")
                startHardwareWorkflow()
            } else {
                setStepState(1, StepState.ERROR, "Payment timed out")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Payment Timeout")
                    .setMessage("Payment confirmation timed out. If you were charged and no locker opened, please contact support with tracking number: $orderId")
                    .setPositiveButton("OK") { _, _ -> (requireActivity() as SendPackageActivity).exitToHome() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun startHardwareWorkflow() {
        lifecycleScope.launch {
            try {
                try {
                    printerDriver.initialize()
                } catch (e: Exception) {
                    Log.e("ProcessingFragment", "Printer init error: ${e.message}")
                }

                // Step 2: Print receipt
                setStepState(2, StepState.IN_PROGRESS, "Printing your receipt...")
                printCustomerReceipt()
                delay(3000)
                setStepState(2, StepState.DONE, "Receipt printed")

                // Step 3: Print label
                setStepState(3, StepState.IN_PROGRESS, "Printing waybill label...")
                printBarcodeLabel()
                delay(3000)
                setStepState(3, StepState.DONE, "Label printed")

                // Security photo
                try {
                    SecurityCameraManager.getInstance(requireContext()).captureSecurityPhoto(
                        reason = PhotoReason.CLIENT_DEPOSIT,
                        referenceId = sendPackageActivity.sendPackageData.orderId,
                        userId = prefs.getUserMobile() ?: ""
                    )
                } catch (e: Exception) {
                    Log.e("ProcessingFragment", "Camera error: ${e.message}")
                }

                // Step 4: Open Locker
                val lockNumber = sendPackageActivity.sendPackageData.assignedLockNumber
                setStepState(4, StepState.IN_PROGRESS, "Opening locker $lockNumber...")
                
                try {
                    openLocker(lockNumber)
                    delay(500)
                    
                    binding.cardLockerAssigned.visibility = View.VISIBLE
                    binding.tvLockerNumber.text = lockNumber.toString()
                    setStepState(4, StepState.DONE, "Locker $lockNumber is open")
    
                    startDoorMonitoring(lockNumber)
                } catch (e: Exception) {
                    Log.e("ProcessingFragment", "Locker hardware error", e)
                    setStepState(4, StepState.ERROR, "Hardware Emulator Bypass")
                    
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Hardware Disconnected")
                        .setMessage("Locker hardware is offline (${e.message}).\n\nSince your payment was already approved, click below to simulate the physical door bypass and complete the test flow.")
                        .setPositiveButton("Simulate Drop-off") { _, _ ->
                            confirmSenderDropoff()
                            showCompletion()
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                setStepState(4, StepState.ERROR, "System Error")
                Log.e("ProcessingFragment", "Unexpected system error", e)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Unexpected Error")
                    .setMessage("An unexpected error occurred: ${e.message}")
                    .setPositiveButton("Finish") { _, _ -> (requireActivity() as SendPackageActivity).exitToHome() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private suspend fun printCustomerReceipt() {
        try {
            val data = sendPackageActivity.sendPackageData
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            printerDriver.printText("CUSTOMER RECEIPT\n", fontSize = 2, bold = true, centered = true)
            printerDriver.printText("--------------------------------\n")
            printerDriver.printText("Date: $timestamp\n")
            printerDriver.printText("Tracking: ${data.trackingNumber}\n")
            printerDriver.printText("Recipient: ${data.recipientName} ${data.recipientSurname}\n")
            printerDriver.printText("Mobile: ${data.recipientMobile}\n")
            printerDriver.printText("Size: ${data.packageSize?.displayName ?: "-"}\n")
            printerDriver.printText("Amount Paid: ${data.currency?.symbol ?: "$"}${String.format("%.2f", data.orderPrice)}\n")
            printerDriver.printText("Payment: ${data.paymentMethod?.displayName ?: "PRE-PAID"}\n")
            printerDriver.printText("--------------------------------\n")
            printerDriver.printText("Thank you for using ZimPudo!\n", bold = true, centered = true)
            printerDriver.printText("\n\n\n")
            printerDriver.feedAndCut()
        } catch (e: Exception) {
            Log.e("ProcessingFragment", "Print receipt error: ${e.message}")
        }
    }

    private suspend fun printBarcodeLabel() {
        try {
            val data = sendPackageActivity.sendPackageData
            printerDriver.printText("ZimPudo Waybill Label\n", fontSize = 2, bold = true, centered = true)
            printerDriver.printText("Tracking: ${data.trackingNumber}\n")
            printerDriver.printText("To: ${data.recipientName} ${data.recipientSurname}\n")
            printerDriver.printText("Size: ${data.packageSize?.displayName ?: "-"}\n")
            printerDriver.printText("From: ${data.recipientCityName}\n")
            printerDriver.printCode128(data.trackingNumber)
            printerDriver.printText("\n\n")
            printerDriver.feedAndCut()
        } catch (e: Exception) {
            Log.e("ProcessingFragment", "Print barcode error: ${e.message}")
        }
    }

    private suspend fun openLocker(lockNumber: Int) {
        val connected = lockerController.connect()
        if (!connected) throw Exception("Failed to connect to locker board")

        val result = lockerController.unlockLock(lockNumber)
        if (result.status == WinnsenProtocol.LockStatus.COOLDOWN) {
            throw Exception("Locker in cooldown. Try again in 15 seconds.")
        } else if (!result.success) {
            throw Exception("Failed to unlock locker: ${result.errorMessage}")
        }
    }

    private fun startDoorMonitoring(lockNumber: Int) {
        val hw = HardwareManager.getInstance(requireContext())
        activeDoorMonitor = DoorMonitor(lockerController, lockNumber, lifecycleScope)
        activeDoorMonitor?.start(
            onDoorOpenTooLong = {
                hw.speaker.startDoorCloseReminder()
            },
            onDoorTimeout = {
                hw.speaker.stopDoorCloseReminder()
            },
            onDoorClosed = {
                hw.speaker.stopDoorCloseReminder()
                lifecycleScope.launch {
                    try { lockerController.disconnect() } catch (_: Exception) {}
                    confirmSenderDropoff()
                    showCompletion()
                }
            }
        )
    }

    private fun confirmSenderDropoff() {
        lifecycleScope.launch {
            try {
                val data = sendPackageActivity.sendPackageData
                val token = prefs.getAccessToken().orEmpty()
                if (token.isBlank() || data.orderId.isBlank()) return@launch

                if (data.cellId.isBlank()) {
                    Log.w("ProcessingFragment", "Skipping sender dropoff API — no cellId assigned (Emulator bypass?)")
                    return@launch
                }

                apiRepository.senderDropoff(data.orderId, data.cellId, token)
            } catch (e: Exception) {
                Log.w("ProcessingFragment", "Sender dropoff error: ${e.message}")
            }
        }
    }

    private fun showCompletion() {
        binding.tvThankYouCountdown.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            for (i in 5 downTo 1) {
                binding.tvThankYouCountdown.text = "Thank you! Your package is secure.\nReturning to main menu in ${i}s..."
                delay(1000)
            }
            (requireActivity() as SendPackageActivity).exitToHome()
        }
    }

    private enum class StepState { INACTIVE, IN_PROGRESS, DONE, ERROR }

    private fun setStepState(stepNumber: Int, state: StepState, text: String) {
        val layout = when (stepNumber) {
            1 -> binding.stepPayment
            2 -> binding.stepReceipt
            3 -> binding.stepLabel
            4 -> binding.stepLocker
            else -> return
        }

        val progress = when (stepNumber) {
            1 -> binding.progressPayment
            2 -> binding.progressReceipt
            3 -> binding.progressLabel
            4 -> binding.progressLocker
            else -> return
        }

        val checked = when (stepNumber) {
            1 -> binding.ivPaymentDone
            2 -> binding.ivReceiptDone
            3 -> binding.ivLabelDone
            4 -> binding.ivLockerDone
            else -> return
        }

        val textView = when (stepNumber) {
            1 -> binding.tvPaymentText
            2 -> binding.tvReceiptText
            3 -> binding.tvLabelText
            4 -> binding.tvLockerText
            else -> return
        }

        // Set opacity
        layout?.alpha = if (state == StepState.INACTIVE) 0.4f else 1.0f

        // Set text
        if (text.isNotBlank()) {
            textView?.text = text
        }

        when (state) {
            StepState.INACTIVE -> {
                progress?.visibility = View.GONE
                checked?.visibility = View.GONE
            }
            StepState.IN_PROGRESS -> {
                progress?.visibility = View.VISIBLE
                checked?.visibility = View.GONE
                textView?.setTextColor(resources.getColor(com.blitztech.pudokiosk.R.color.zimpudo_primary, null))
            }
            StepState.DONE -> {
                progress?.visibility = View.GONE
                checked?.visibility = View.VISIBLE
                textView?.setTextColor(resources.getColor(com.blitztech.pudokiosk.R.color.success, null))
            }
            StepState.ERROR -> {
                progress?.visibility = View.GONE
                checked?.visibility = View.GONE
                textView?.setTextColor(resources.getColor(com.blitztech.pudokiosk.R.color.error, null))
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
