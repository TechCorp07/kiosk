package com.blitztech.pudokiosk.ui.customer

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.order.OrderTrackerDto
import com.blitztech.pudokiosk.data.repository.ApiRepository
import com.blitztech.pudokiosk.databinding.DialogParcelDetailsBinding
import com.blitztech.pudokiosk.databinding.ItemTimelineEventBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog to show full tracking timeline for a specific active tracking number.
 */
class ParcelDetailsDialog(
    context: Context,
    private val trackingNumber: String,
    private val api: ApiRepository
) : Dialog(context) {

    private lateinit var binding: DialogParcelDetailsBinding
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        binding = DialogParcelDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make window match full kiosk size styling with transparency background
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        binding.btnClose.setOnClickListener { dismiss() }
        binding.tvTrackingTitle.text = "Tracking: $trackingNumber"

        loadTrackingHistory()
    }

    private fun loadTrackingHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.rvTimeline.visibility = View.GONE

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                api.trackOrder(trackingNumber)
            }
            binding.progressBar.visibility = View.GONE
            
            when (result) {
                is NetworkResult.Success -> {
                    val events = result.data.content
                    if (events.isEmpty()) {
                        showError("No tracking history found.")
                    } else {
                        showTimeline(events)
                    }
                }
                is NetworkResult.Error -> {
                    showError("Failed to track package: ${result.message}")
                }
                is NetworkResult.Loading<*> -> {}
            }
        }
    }

    private fun showTimeline(events: List<OrderTrackerDto>) {
        binding.rvTimeline.visibility = View.VISIBLE
        binding.rvTimeline.layoutManager = LinearLayoutManager(context)
        binding.rvTimeline.adapter = TimelineAdapter(events)
    }

    private fun showError(msg: String) {
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = msg
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    // ─────────────────────────────────────────────────────────────
    // Inner Adapter for Timeline Events
    // ─────────────────────────────────────────────────────────────
    private inner class TimelineAdapter(private val items: List<OrderTrackerDto>) :
        RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemTimelineEventBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflate = ItemTimelineEventBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(inflate)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                tvActivity.text = item.activity ?: "UNKNOWN"
                tvDescription.text = item.description ?: ""
                
                // Truncate timestamp if it has milliseconds "2026-04-05T14:00:23.1232Z" -> "2026-04-05 14:00"
                val rawDate = item.createdDate ?: ""
                tvDate.text = rawDate.replace("T", " ").substringBeforeLast(".")

                // Line visibility for first/last items to look beautiful
                vLineTop.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                vLineBottom.visibility = if (position == items.size - 1) View.INVISIBLE else View.VISIBLE
            }
        }

        override fun getItemCount() = items.size
    }
}
