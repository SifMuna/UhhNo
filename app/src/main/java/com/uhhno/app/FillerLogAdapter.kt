package com.uhhno.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FillerLogAdapter : RecyclerView.Adapter<FillerLogAdapter.ViewHolder>() {

    private val items = mutableListOf<FillerEntry>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvWord: TextView = view.findViewById(R.id.tvFillerWord)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filler_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.tvIndex.text = "${position + 1}"
        holder.tvWord.text = "\"${entry.word}\""
        holder.tvTimestamp.text = entry.timestamp
    }

    override fun getItemCount() = items.size

    fun addFiller(entry: FillerEntry) {
        items.add(entry)
        notifyItemInserted(items.size - 1)
    }

    fun clearAll() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }
}
