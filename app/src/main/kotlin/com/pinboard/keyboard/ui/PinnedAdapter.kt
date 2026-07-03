package com.pinboard.keyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pinboard.keyboard.R
import com.pinboard.keyboard.data.PinnedMessage

class PinnedAdapter(
    private val onInsert: (PinnedMessage) -> Unit,
    private val onEdit: (PinnedMessage) -> Unit,
    private val onDelete: (PinnedMessage) -> Unit
) : RecyclerView.Adapter<PinnedAdapter.VH>() {

    private val items = mutableListOf<PinnedMessage>()

    fun submitList(newItems: List<PinnedMessage>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].id == newItems[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    inner class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.messageText)
        val editBtn: TextView = itemView.findViewById(R.id.btnEdit)
        val deleteBtn: TextView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pinned_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        holder.itemView.setOnClickListener { onInsert(item) }
        holder.editBtn.setOnClickListener { onEdit(item) }
        holder.deleteBtn.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size
}
