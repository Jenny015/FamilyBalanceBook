package com.example.familybalance.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.familybalance.R
import com.example.familybalance.data.models.Entry

class EntryAdapter(
    private val currentDeviceRole: Int,
    private val onEntryClick: (Entry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.EntryViewHolder>() {

    private val entriesList = mutableListOf<Entry>()

    // Replaces or initializes the list (useful for initial 5 items)
    @SuppressLint("NotifyDataSetChanged")
    fun setEntries(newList: List<Entry>) {
        entriesList.clear()
        entriesList.addAll(newList)
        notifyDataSetChanged()
    }

    // Appends older entries to the top when "< view more >" is tapped
    fun appendOlderEntries(olderList: List<Entry>) {
        if (olderList.isEmpty()) return
        entriesList.addAll(0, olderList)
        notifyItemRangeInserted(0, olderList.size)
    }

    // Helper to find the current oldest ID loaded in memory
    fun getOldestLoadedId(): Int? {
        return entriesList.firstOrNull()?.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false)
        return EntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = entriesList[position]
        holder.bind(entry, currentDeviceRole, onEntryClick)
    }

    override fun getItemCount(): Int = entriesList.size

    class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutMom: LinearLayout = itemView.findViewById(R.id.layout_mom_entry)
        private val tvMomTitle: TextView = itemView.findViewById(R.id.tv_mom_title)
        private val tvMomAmount: TextView = itemView.findViewById(R.id.tv_mom_amount)

        private val layoutDaughter: LinearLayout = itemView.findViewById(R.id.layout_daughter_entry)
        private val tvDaughterTitle: TextView = itemView.findViewById(R.id.tv_daughter_title)
        private val tvDaughterAmount: TextView = itemView.findViewById(R.id.tv_daughter_amount)

        @SuppressLint("DefaultLocale")
        fun bind(entry: Entry, deviceRole: Int, clickListener: (Entry) -> Unit) {

            // Format amount to 1 decimal place as requested
            val formattedAmount = "$ ${String.format("%.1f", entry.amount)}"

            if (entry.role == 0) {
                // It's Mom's (Credit) entry -> Show left side, Hide right side
                layoutMom.visibility = View.VISIBLE
                layoutDaughter.visibility = View.GONE

                tvMomTitle.text = entry.title
                tvMomAmount.text = formattedAmount

                // Enable Marquee Scrolling effect for long titles
                tvMomTitle.isSelected = true
            } else {
                // It's Daughter's (Debit) entry -> Show right side, Hide left side
                layoutMom.visibility = View.GONE
                layoutDaughter.visibility = View.VISIBLE

                tvDaughterTitle.text = entry.title
                tvDaughterAmount.text = formattedAmount

                // Enable Marquee Scrolling effect for long titles
                tvDaughterTitle.isSelected = true
            }

            // Set up click interactions
            itemView.setOnClickListener {
                clickListener(entry)
            }
        }
    }
}