package com.blitztech.pudokiosk.ui.main

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.audio.LockerReminder
import com.blitztech.pudokiosk.data.events.EventType
import com.blitztech.pudokiosk.data.events.Outbox
import com.blitztech.pudokiosk.data.repository.BackendRepository
import com.blitztech.pudokiosk.utils.Ids
import kotlinx.coroutines.launch
import com.blitztech.pudokiosk.data.sync.WorkScheduler
import com.blitztech.pudokiosk.payment.*
import com.blitztech.pudokiosk.data.AuditLogger
import com.blitztech.pudokiosk.prefs.Prefs


class SenderActivity : AppCompatActivity() {

    private enum class Step { LOGIN, RECIPIENT, SIZE, PAYMENT, LOCKER }
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
