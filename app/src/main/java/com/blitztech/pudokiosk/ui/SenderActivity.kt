package com.blitztech.pudokiosk.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.audio.LockerReminder
import com.blitztech.pudokiosk.data.events.EventType
import com.blitztech.pudokiosk.data.events.Outbox
import com.blitztech.pudokiosk.data.repo.BackendRepository
import com.blitztech.pudokiosk.util.Ids
import kotlinx.coroutines.launch
import com.blitztech.pudokiosk.data.sync.WorkScheduler
import com.blitztech.pudokiosk.payment.*
import com.blitztech.pudokiosk.data.AuditLogger
import com.blitztech.pudokiosk.prefs.Prefs


class SenderActivity : AppCompatActivity() {

    private enum class Step { LOGIN, RECIPIENT, SIZE, PAYMENT, LOCKER }
    private var simulateHardware = true // set to false when you plug in devices
    private lateinit var locker: `LockerController.kt`
    private var useBackupIM30 = false // set true to exercise IM30 path
    private lateinit var terminal: PaymentTerminal

    private lateinit var tvStep: TextView
    private lateinit var etPhone: EditText
    private lateinit var cbNew: CheckBox
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var prefs: Prefs

    private lateinit var stepRecipient: View
    private lateinit var etRecName: EditText
    private lateinit var etRecPhone: EditText

    private lateinit var stepSize: View
    private lateinit var spSize: Spinner
    private lateinit var cbDecl: CheckBox

    private lateinit var stepPayment: View
    private lateinit var etAmount: EditText
    private lateinit var btnPay: Button

    private lateinit var stepLocker: View
    private lateinit var tvLocker: TextView
    private lateinit var btnOpenLocker: Button
    private lateinit var btnDoorClosed: Button

    private lateinit var btnNext: Button
    private lateinit var btnCancel: Button

    private var current = Step.LOGIN
    private val backend = BackendRepository()
    private var lockerId: String? = null
    private var tracking: String = "TRK-" + Ids.uuid().take(8).uppercase()
    private var locale: String = "en"
    private var reminder: LockerReminder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)
        prefs = Prefs(this)
        useBackupIM30 = prefs.isUseBackupIM30()
        locker = `LockerController.kt`(this, simulate = simulateHardware)
        terminal = Im30MdbDriver(this, simulate = true) // set simulate=false on device

        tvStep = findViewById(R.id.tvStep)
        etPhone = findViewById(R.id.etPhone)
        cbNew = findViewById(R.id.cbNewUser)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)

        stepRecipient = findViewById(R.id.stepRecipient)
        etRecName = findViewById(R.id.etRecName)
        etRecPhone = findViewById(R.id.etRecPhone)

        stepSize = findViewById(R.id.stepSize)
        spSize = findViewById(R.id.spSize)
        cbDecl = findViewById(R.id.cbDeclaration)

        stepPayment = findViewById(R.id.stepPayment)
        etAmount = findViewById(R.id.etAmount)
        btnPay = findViewById(R.id.btnPay)

        stepLocker = findViewById(R.id.stepLocker)
        tvLocker = findViewById(R.id.tvLocker)
        btnOpenLocker = findViewById(R.id.btnOpenLocker)
        btnDoorClosed = findViewById(R.id.btnDoorClosed)

        btnNext = findViewById(R.id.btnNext)
        btnCancel = findViewById(R.id.btnCancel)

        // size options
        spSize.adapter = ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, listOf("XS","S","M","L","XL")
        )

        // show/hide extra fields on signup
        cbNew.setOnCheckedChangeListener { _, checked ->
            etName.visibility = if (checked) View.VISIBLE else View.GONE
            etEmail.visibility = if (checked) View.VISIBLE else View.GONE
        }

        btnCancel.setOnClickListener { finish() }

        btnNext.setOnClickListener { onNext() }
        btnPay.setOnClickListener { onPay() }
        btnOpenLocker.setOnClickListener { onOpenLocker() }
        btnDoorClosed.setOnClickListener { onDoorClosed() }

        render()
    }

    private fun render() {
        tvStep.text = when (current) {
            Step.LOGIN -> "Step 1: Login / Signup"
            Step.RECIPIENT -> "Step 2: Recipient"
            Step.SIZE -> "Step 3: Size & Declaration"
            Step.PAYMENT -> "Step 4: Payment (Backend)"
            Step.LOCKER -> "Step 5: Locker"
        }

        // visibility
        val visLogin = current == Step.LOGIN
        etPhone.visibility = if (visLogin) View.VISIBLE else View.GONE
        cbNew.visibility = if (visLogin) View.VISIBLE else View.GONE
        etName.visibility = if (visLogin && cbNew.isChecked) View.VISIBLE else View.GONE
        etEmail.visibility = if (visLogin && cbNew.isChecked) View.VISIBLE else View.GONE

        stepRecipient.visibility = if (current == Step.RECIPIENT) View.VISIBLE else View.GONE
        stepSize.visibility = if (current == Step.SIZE) View.VISIBLE else View.GONE
        stepPayment.visibility = if (current == Step.PAYMENT) View.VISIBLE else View.GONE
        stepLocker.visibility = if (current == Step.LOCKER) View.VISIBLE else View.GONE

        btnNext.visibility = if (current == Step.PAYMENT) View.GONE else View.VISIBLE
    }

    private fun onNext() {
        when (current) {
            Step.LOGIN -> {
                val phone = etPhone.text.toString().trim()
                if (phone.isEmpty()) { toast("Enter phone"); return }
                if (cbNew.isChecked) {
                    val name = etName.text.toString().trim()
                    if (name.isEmpty()) { toast("Enter name"); return }
                    lifecycleScope.launch {
                        Outbox.enqueue(EventType.USER_SIGNUP, mapOf(
                            "userId" to "usr_${Ids.uuid().take(8)}",
                            "phone" to phone,
                            "name" to name,
                            "email" to etEmail.text.toString().trim().ifEmpty { null },
                            "kycStatus" to "PENDING"
                        ))
                    }
                }
                current = Step.RECIPIENT
            }
            Step.RECIPIENT -> {
                if (etRecName.text.isBlank() || etRecPhone.text.isBlank()) {
                    toast("Fill recipient name & phone"); return
                }
                current = Step.SIZE
            }
            Step.SIZE -> {
                if (!cbDecl.isChecked) { toast("You must accept declaration"); return }
                current = Step.PAYMENT
            }
            Step.PAYMENT -> {} // handled by btnPay
            Step.LOCKER -> { /* no-op */ }
        }
        render()
    }

    private fun onPay() {
        val cents = etAmount.text.toString().toIntOrNull()
        if (cents == null || cents <= 0) { toast("Enter valid amount (cents)"); return }

        // ----- IM30 backup path -----
        if (useBackupIM30) {
            lifecycleScope.launch {
                try {
                    if (!terminal.init()) {
                        AuditLogger.log("ERROR", "PAYMENT_ERROR", "method=IM30 msg=not_ready")
                        toast("IM30 not ready")
                        return@launch
                    }
                    val res = terminal.pay(PaymentRequest(cents))
                    when (res) {
                        is PaymentResult.Approved -> {
                            AuditLogger.log("INFO", "PAYMENT_SUCCESS", "amount=$cents method=IM30")
                            Outbox.enqueue(EventType.PAYMENT_RESULT, mapOf(
                                "status" to "SUCCESS",
                                "method" to "IM30",
                                "amountCents" to cents,
                                "auth" to (res.authCode ?: "")
                            ))
                            WorkScheduler.flushOutboxNow(this@SenderActivity)

                            // Create parcel record (client-side ID) to mirror backend path
                            Outbox.enqueue(EventType.PARCEL_CREATED, mapOf(
                                "parcelId" to tracking,
                                "senderPhone" to etPhone.text.toString().trim(),
                                "recipientPhone" to etRecPhone.text.toString().trim(),
                                "size" to spSize.selectedItem.toString()
                            ))
                            WorkScheduler.flushOutboxNow(this@SenderActivity)

                            // Allocate locker
                            val alloc = backend.allocateLocker(spSize.selectedItem.toString())
                            lockerId = alloc.lockerId
                            tvLocker.text = "Locker: ${alloc.lockerId} (${alloc.size})"
                            current = Step.LOCKER
                            render()
                        }
                        is PaymentResult.Declined -> {
                            AuditLogger.log("ERROR", "PAYMENT_FAILED", "amount=$cents method=IM30 reason=${res.reason}")
                            Outbox.enqueue(EventType.PAYMENT_RESULT, mapOf(
                                "status" to "FAILED",
                                "method" to "IM30",
                                "amountCents" to cents,
                                "reason" to res.reason
                            ))
                            WorkScheduler.flushOutboxNow(this@SenderActivity)
                            toast("Payment declined: ${res.reason}")
                        }
                        is PaymentResult.Error -> {
                            AuditLogger.log("ERROR", "PAYMENT_ERROR", "amount=$cents method=IM30 msg=${res.message}")
                            Outbox.enqueue(EventType.PAYMENT_RESULT, mapOf(
                                "status" to "ERROR",
                                "method" to "IM30",
                                "amountCents" to cents,
                                "message" to res.message
                            ))
                            WorkScheduler.flushOutboxNow(this@SenderActivity)
                            toast("Payment error: ${res.message}")
                        }
                    }
                } catch (e: Exception) {
                    AuditLogger.log("ERROR", "PAYMENT_ERROR", "amount=$cents method=IM30 msg=${e.message}")
                    toast("Payment error")
                }
            }
            return
        }

        // ----- Backend (primary) path -----
        lifecycleScope.launch {
            try {
                val res = backend.initPayment(cents)
                Outbox.enqueue(EventType.PAYMENT_RESULT, mapOf(
                    "paymentId" to res.paymentId,
                    "status" to res.status,
                    "amountCents" to cents,
                    "method" to "BACKEND"
                ))
                WorkScheduler.flushOutboxNow(this@SenderActivity)

                if (res.status == "SUCCESS") {
                    AuditLogger.log("INFO", "PAYMENT_SUCCESS", "amount=$cents method=BACKEND id=${res.paymentId}")

                    // Create parcel record (client-side ID)
                    Outbox.enqueue(EventType.PARCEL_CREATED, mapOf(
                        "parcelId" to tracking,
                        "senderPhone" to etPhone.text.toString().trim(),
                        "recipientPhone" to etRecPhone.text.toString().trim(),
                        "size" to spSize.selectedItem.toString()
                    ))
                    WorkScheduler.flushOutboxNow(this@SenderActivity)

                    // Allocate locker
                    val alloc = backend.allocateLocker(spSize.selectedItem.toString())
                    lockerId = alloc.lockerId
                    tvLocker.text = "Locker: ${alloc.lockerId} (${alloc.size})"
                    current = Step.LOCKER
                    render()
                } else {
                    AuditLogger.log("ERROR", "PAYMENT_FAILED", "amount=$cents method=BACKEND id=${res.paymentId}")
                    toast("Payment failed")
                }
            } catch (e: Exception) {
                AuditLogger.log("ERROR", "PAYMENT_ERROR", "amount=$cents method=BACKEND msg=${e.message}")
                toast("Payment error")
            }
        }
    }

    private fun onOpenLocker() {
        val id = lockerId ?: run { toast("Locker not allocated"); return }
        lifecycleScope.launch {
            Outbox.enqueue(EventType.LOCKER_OPEN_REQUEST, mapOf("lockerId" to id))
            WorkScheduler.flushOutboxNow(this@SenderActivity)

            val ok = locker.openLocker(id, retries = 2)
            if (ok) {
                AuditLogger.log("INFO", "LOCKER_OPEN_SUCCESS", "lockerId=$id")
                Outbox.enqueue(EventType.LOCKER_OPEN_SUCCESS, mapOf("lockerId" to id))
                WorkScheduler.flushOutboxNow(this@SenderActivity)

                WorkScheduler.flushOutboxNow(this@SenderActivity)

                reminder?.stop()
                reminder = LockerReminder(this@SenderActivity, locale).also { it.start() }
                toast("Locker opened. Place parcel + ticket, then close. Tap 'I closed the door'.")
            } else {
                AuditLogger.log("ERROR", "LOCKER_OPEN_FAIL", "lockerId=$id reason=NoACK")
                Outbox.enqueue(EventType.LOCKER_OPEN_FAIL, mapOf("lockerId" to id, "reason" to "No ACK"))
                WorkScheduler.flushOutboxNow(this@SenderActivity)
                toast("Failed to open locker. A technician will be notified.")
            }
        }
    }

    private fun onDoorClosed() {
        val id = lockerId
        lifecycleScope.launch {
            if (id != null && !locker.isClosed(id)) {
                toast("Door still open. Please close it.")
                return@launch
            }
            reminder?.stop()
            AuditLogger.log("INFO", "SENDER_LOCKER_CLOSED", "lockerId=$id tracking=$tracking")
            toast("Thank you. Parcel recorded as in locker.")
            finish()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
