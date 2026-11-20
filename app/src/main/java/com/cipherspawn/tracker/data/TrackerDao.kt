package com.cipherspawn.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracked_channels ORDER BY updatedAt DESC")
    fun observeTracked(): Flow<List<TrackedChannel>>

    @Query("SELECT * FROM tracked_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getTracked(channelId: String): TrackedChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTracked(channel: TrackedChannel)

    @Query("DELETE FROM tracked_channels WHERE channelId = :channelId")
    suspend fun removeTracked(channelId: String)

    @Insert
    suspend fun addSnapshot(snapshot: ChannelSnapshot)

    @Query("SELECT * FROM channel_snapshots WHERE channelId = :channelId ORDER BY recordedAt DESC LIMIT :limit")
    fun observeSnapshots(channelId: String, limit: Int = 30): Flow<List<ChannelSnapshot>>

    @Query("SELECT * FROM tracked_channels")
    suspend fun getAllTracked(): List<TrackedChannel>
}
