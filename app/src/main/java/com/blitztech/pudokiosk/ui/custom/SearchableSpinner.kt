package com.blitztech.pudokiosk.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.blitztech.pudokiosk.R
import com.google.android.material.textfield.TextInputEditText

data class SpinnerItem(
    val id: String,
    val name: String
)

class SearchableSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextInputEditText(context, attrs, defStyleAttr) {

    private var items: List<SpinnerItem> = emptyList()
    private var filteredItems: List<SpinnerItem> = emptyList()
    private var selectedItem: SpinnerItem? = null
    private var onItemSelectedListener: ((SpinnerItem?) -> Unit)? = null
    private var dialog: AlertDialog? = null

    init {
        isFocusable = false
        isClickable = true
        setOnClickListener { showDropdown() }
    }

    fun setItems(newItems: List<SpinnerItem>) {
        items = newItems
        filteredItems = newItems
    }

    fun setOnItemSelectedListener(listener: (SpinnerItem?) -> Unit) {
        onItemSelectedListener = listener
    }

    fun getSelectedItem(): SpinnerItem? = selectedItem

    fun setSelectedItem(item: SpinnerItem?) {
        selectedItem = item
        setText(item?.name ?: "")
        onItemSelectedListener?.invoke(item)
    }

    private fun showDropdown() {
        if (items.isEmpty()) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_searchable_spinner, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.lvItems)

        val adapter = SearchableSpinnerAdapter(context, filteredItems.toMutableList())
        listView.adapter = adapter

        dialog = AlertDialog.Builder(context)
            .setTitle("Select ${hint}")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                val filtered = items.filter {
                    it.name.lowercase().contains(query)
                }
                adapter.updateItems(filtered)
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            setSelectedItem(item)
            dialog?.dismiss()
        }

        dialog?.show()
    }

    private class SearchableSpinnerAdapter(
        context: Context,
        private val items: MutableList<SpinnerItem>
    ) : BaseAdapter() {

        private val inflater = LayoutInflater.from(context)

        fun updateItems(newItems: List<SpinnerItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): SpinnerItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.text = items[position].name
            textView.setPadding(32, 24, 32, 24)
            return view
        }
    }
}