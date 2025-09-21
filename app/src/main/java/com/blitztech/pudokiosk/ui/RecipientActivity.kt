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
import com.blitztech.pudokiosk.data.repo.AuthRepository
import com.blitztech.pudokiosk.data.repo.RecipientRepository
import com.blitztech.pudokiosk.data.net.ParcelItem
import com.blitztech.pudokiosk.data.sync.WorkScheduler
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.secure.PinStore
import kotlinx.coroutines.launch
import com.blitztech.pudokiosk.data.AuditLogger
import com.blitztech.pudokiosk.prefs.Prefs

class RecipientActivity : AppCompatActivity() {

    private enum class Step { OTP, PIN, PARCELS }

    private lateinit var tvStep: TextView
    private lateinit var stepPhone: View
    private lateinit var etPhone: EditText
    private lateinit var etOtp: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerifyOtp: Button

    private lateinit var stepPin: View
    private lateinit var etPin1: EditText
    private lateinit var etPin2: EditText
    private lateinit var btnSavePin: Button
    private lateinit var prefs: Prefs

    private lateinit var stepParcels: View
    private lateinit var listParcels: ListView
    private lateinit var tvSelected: TextView
    private lateinit var btnOpenLocker: Button
    private lateinit var btnClosed: Button

    private val auth = AuthRepository(useStub = true) // set false when backend ready
    private val repo = RecipientRepository(useStub = true)
    private lateinit var pinStore: PinStore
    private lateinit var locker: LockerController
    private var reminder: LockerReminder? = null
    private var simulateHardware = true

    private var current = Step.OTP
    private var userId: String? = null
    private var selected: ParcelItem? = null
    private var locale = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipient)

        prefs = Prefs(this)
        locker = LockerController(this, simulate = prefs.isSimHardware())

        tvStep = findViewById(R.id.tvStep)
        stepPhone = findViewById(R.id.stepPhone)
        etPhone = findViewById(R.id.etPhone)
        etOtp = findViewById(R.id.etOtp)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)

        stepPin = findViewById(R.id.stepPin)
        etPin1 = findViewById(R.id.etPin1)
        etPin2 = findViewById(R.id.etPin2)
        btnSavePin = findViewById(R.id.btnSavePin)

        stepParcels = findViewById(R.id.stepParcels)
        listParcels = findViewById(R.id.listParcels)
        tvSelected = findViewById(R.id.tvSelected)
        btnOpenLocker = findViewById(R.id.btnOpenLocker)
        btnClosed = findViewById(R.id.btnClosed)

        pinStore = PinStore(this)

        btnSendOtp.setOnClickListener { onSendOtp() }
        btnVerifyOtp.setOnClickListener { onVerifyOtp() }
        btnSavePin.setOnClickListener { onSavePin() }
        btnOpenLocker.setOnClickListener { onOpenLocker() }
        btnClosed.setOnClickListener { onClosed() }

        listParcels.setOnItemClickListener { _, _, position, _ ->
            val items = getItems() // uses listParcels.tag under the hood
            selected = if (position in items.indices) items[position] else null
            tvSelected.text = selected?.let { "Selected: ${it.tracking} (${it.lockerId})" } ?: "No parcel selected"
            btnOpenLocker.isEnabled = selected != null
        }

        render()
    }

    private fun render() {
        tvStep.text = when(current) {
            Step.OTP -> "Step 1: Phone + OTP"
            Step.PIN -> "Step 2: Create PIN"
            Step.PARCELS -> "Step 3: Collect (one at a time)"
        }
        stepPhone.visibility = if (current == Step.OTP) View.VISIBLE else View.GONE
        stepPin.visibility = if (current == Step.PIN) View.VISIBLE else View.GONE
        stepParcels.visibility = if (current == Step.PARCELS) View.VISIBLE else View.GONE
    }

    private fun onSendOtp() {
        val phone = etPhone.text.toString().trim()
        if (phone.isEmpty()) { toast("Enter phone"); return }
        lifecycleScope.launch {
            try {
                auth.requestOtp(phone)
                toast("OTP sent")
            } catch (e: Exception) {
                toast("Failed to send OTP")
            }
        }
    }

    private fun onVerifyOtp() {
        val phone = etPhone.text.toString().trim()
        val otp = etOtp.text.toString().trim()
        if (phone.isEmpty() || otp.isEmpty()) { toast("Enter phone & OTP"); return }
        lifecycleScope.launch {
            try {
                val r = auth.verifyOtp(phone, otp)
                userId = r.userId
                // enforce PIN creation if first time OR no local PIN set
                current = if (r.firstTime || !pinStore.hasPin(r.userId)) Step.PIN else Step.PARCELS
                render()
                if (current == Step.PARCELS) loadParcels()
            } catch (e: Exception) {
                toast("OTP verify failed")
            }
        }
    }

    private fun onSavePin() {
        val u = userId ?: return
        val p1 = etPin1.text.toString()
        val p2 = etPin2.text.toString()
        if (p1.length < 4) { toast("PIN too short"); return }
        if (p1 != p2) { toast("PINs do not match"); return }
        pinStore.setPin(u, p1)
        current = Step.PARCELS
        render()
        loadParcels()
    }

    private fun loadParcels() {
        val u = userId ?: return
        lifecycleScope.launch {
            try {
                val items = repo.listParcels(u)
                val adapter = ArrayAdapter(this@RecipientActivity, android.R.layout.simple_list_item_1,
                    items.map { "${it.tracking} — ${it.size} — ${it.lockerId}" })
                listParcels.adapter = adapter
                // keep the objects aligned by using the same order in a local list
                listParcels.tag = items
                selected = items.firstOrNull()
                tvSelected.text = selected?.let { "Selected: ${it.tracking} (${it.lockerId})" } ?: "No parcels."
                btnOpenLocker.isEnabled = selected != null
            } catch (e: Exception) {
                toast("Failed to load parcels")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getItems(): List<ParcelItem> =
        (listParcels.tag as? List<ParcelItem>) ?: emptyList()

    private fun onOpenLocker() {
        val item = selected ?: run { toast("Select a parcel"); return }
        lifecycleScope.launch {
            Outbox.enqueue(EventType.LOCKER_OPEN_REQUEST, mapOf("lockerId" to item.lockerId, "parcelId" to item.parcelId))
            WorkScheduler.flushOutboxNow(this@RecipientActivity)

            val ok = locker.openLocker(item.lockerId, retries = 2)
            if (ok) {
                AuditLogger.log("INFO", "LOCKER_OPEN_SUCCESS", "lockerId=${item.lockerId} parcelId=${item.parcelId}")
                Outbox.enqueue(EventType.LOCKER_OPEN_SUCCESS, mapOf("lockerId" to item.lockerId, "parcelId" to item.parcelId))
                WorkScheduler.flushOutboxNow(this@RecipientActivity)

                reminder?.stop()
                reminder = LockerReminder(this@RecipientActivity, locale).also { it.start() }
                toast("Locker opened. Take parcel, close door, then tap 'I closed the door'.")
            } else {
                AuditLogger.log("ERROR", "LOCKER_OPEN_FAIL", "lockerId=${item.lockerId} parcelId=${item.parcelId} reason=NoACK")
                Outbox.enqueue(EventType.LOCKER_OPEN_FAIL, mapOf("lockerId" to item.lockerId, "parcelId" to item.parcelId, "reason" to "No ACK"))
                WorkScheduler.flushOutboxNow(this@RecipientActivity)
                toast("Failed to open locker. Please seek assistance.")
            }
        }
    }

    private fun onClosed() {
        reminder?.stop()
        val item = selected ?: return
        lifecycleScope.launch {
            if (!locker.isClosed(item.lockerId)) {
                toast("Door still open. Please close it.")
                return@launch
            }
            AuditLogger.log("INFO", "RECIPIENT_LOCKER_CLOSED", "lockerId=${item.lockerId} parcelId=${item.parcelId}")

            // FYI: replace these placeholder events with your real "collected" event later
            Outbox.enqueue(EventType.PAYMENT_RESULT, mapOf("note" to "pickup_no_payment"))
            Outbox.enqueue(EventType.LABEL_PRINTED, mapOf("note" to "n/a"))
            Outbox.enqueue(EventType.PARCEL_CREATED, mapOf("note" to "collected ${item.parcelId}"))
            WorkScheduler.flushOutboxNow(this@RecipientActivity)

            val remaining = getItems().drop(1) // simple demo: remove first
            if (remaining.isEmpty()) {
                toast("All done. Thank you!")
                finish()
            } else {
                listParcels.tag = remaining
                val adapter = ArrayAdapter(this@RecipientActivity, android.R.layout.simple_list_item_1,
                    remaining.map { "${it.tracking} — ${it.size} — ${it.lockerId}" })
                listParcels.adapter = adapter
                selected = remaining.first()
                tvSelected.text = "Selected: ${selected?.tracking} (${selected?.lockerId})"
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
