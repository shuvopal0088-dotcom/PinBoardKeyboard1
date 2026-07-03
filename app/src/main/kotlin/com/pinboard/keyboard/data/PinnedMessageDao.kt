package com.pinboard.keyboard.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface PinnedMessageDao {

    @Query("SELECT * FROM pinned_messages ORDER BY position ASC, createdAt DESC")
    fun observeAll(): LiveData<List<PinnedMessage>>

    @Query("SELECT * FROM pinned_messages ORDER BY position ASC, createdAt DESC")
    suspend fun getAllOnce(): List<PinnedMessage>

    @Query("SELECT * FROM pinned_messages WHERE text LIKE '%' || :query || '%' ORDER BY position ASC, createdAt DESC")
    fun search(query: String): LiveData<List<PinnedMessage>>

    @Insert
    suspend fun insert(message: PinnedMessage): Long

    @Update
    suspend fun update(message: PinnedMessage)

    @Delete
    suspend fun delete(message: PinnedMessage)

    @Query("DELETE FROM pinned_messages")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(messages: List<PinnedMessage>)
}
