package com.blitztech.pudokiosk.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.api.dto.customer.CustomerParcel

/**
 * Adapter for the customer parcel status list in [TrackDeliveryActivity].
 */
class ParcelStatusAdapter(
    private val parcels: List<CustomerParcel>
) : RecyclerView.Adapter<ParcelStatusAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTracking: TextView = view.findViewById(R.id.tvTracking)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvLocker: TextView = view.findViewById(R.id.tvLocker)
        val tvSize: TextView = view.findViewById(R.id.tvSize)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parcel_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val parcel = parcels[position]
        holder.tvTracking.text = parcel.trackingCode
        holder.tvStatus.text = parcel.status.replace("_", " ")
        holder.tvLocker.text = if ((parcel.lockNumber ?: 0) > 0) "Locker ${parcel.lockNumber}" else "—"
        holder.tvSize.text = parcel.parcelSize
        holder.tvDate.text = parcel.updatedAt.take(10)

        // Color-code status
        val statusColor = when (parcel.status.uppercase()) {
            "READY_FOR_COLLECTION" -> R.color.success_green
            "COLLECTED" -> R.color.text_secondary
            "IN_TRANSIT" -> R.color.zimpudo_primary
            "DELIVERED" -> R.color.success_green
            else -> R.color.text_primary
        }
        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, statusColor))
    }

    override fun getItemCount() = parcels.size
}
