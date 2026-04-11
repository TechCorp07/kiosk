package com.blitztech.pudokiosk.ui.courier

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.api.dto.courier.CourierParcel

/**
 * RecyclerView adapter for the courier parcel selection list.
 * Allows multi-selection with a running callback to the host activity.
 */
class CourierParcelAdapter(
    private val parcels: List<CourierParcel>,
    private val onSelectionChanged: (List<CourierParcel>) -> Unit
) : RecyclerView.Adapter<CourierParcelAdapter.VH>() {

    private val selected = mutableSetOf<String>() // parcelId

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelect)
        val tvTracking: TextView = itemView.findViewById(R.id.tvTracking)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        val tvLocker: TextView = itemView.findViewById(R.id.tvLocker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_courier_parcel, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val parcel = parcels[position]
        
        // Format the long UUID into a clean tracking number (e.g. ZIM-A053BF6F)
        val displayTracking = if (parcel.tracking.length >= 8) {
            "ZIM-" + parcel.tracking.takeLast(8).uppercase()
        } else {
            parcel.tracking
        }
        holder.tvTracking.text = displayTracking
        
        holder.tvDetails.text = "${parcel.recipientName}  •  ${parcel.size}"
        holder.tvLocker.text = "Locker ${parcel.lockNumber}"
        holder.cbSelect.isChecked = selected.contains(parcel.parcelId)

        holder.itemView.setOnClickListener {
            if (selected.contains(parcel.parcelId)) selected.remove(parcel.parcelId)
            else selected.add(parcel.parcelId)
            notifyItemChanged(position)
            onSelectionChanged(parcels.filter { it.parcelId in selected })
        }
        holder.cbSelect.setOnClickListener {
            if (selected.contains(parcel.parcelId)) selected.remove(parcel.parcelId)
            else selected.add(parcel.parcelId)
            notifyItemChanged(position)
            onSelectionChanged(parcels.filter { it.parcelId in selected })
        }
    }

    override fun getItemCount() = parcels.size
}
