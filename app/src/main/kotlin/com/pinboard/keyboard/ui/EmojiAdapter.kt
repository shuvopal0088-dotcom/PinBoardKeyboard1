package com.pinboard.keyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pinboard.keyboard.R
import com.pinboard.keyboard.util.EmojiList

class EmojiAdapter(
    private val onInsert: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.VH>() {

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emoji, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val emoji = EmojiList.EMOJIS[position]
        holder.view.text = emoji
        holder.view.setOnClickListener { onInsert(emoji) }
    }

    override fun getItemCount() = EmojiList.EMOJIS.size
}
