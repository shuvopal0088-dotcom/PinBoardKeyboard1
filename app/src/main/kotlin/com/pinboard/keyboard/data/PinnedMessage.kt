package com.pinboard.keyboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_messages")
data class PinnedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val position: Int = 0
)
