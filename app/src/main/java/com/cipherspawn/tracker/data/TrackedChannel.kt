package com.cipherspawn.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_channels")
data class TrackedChannel(
    @PrimaryKey val channelId: String,
    val title: String,
    val handle: String?,
    val subscriberCount: Long?,
    val videoCount: Long,
    val viewCount: Long,
    val likesTotal: Long?,
    val likesStatus: String?,
    val updatedAt: String,
    val thumbnailUrl: String? = null
)

@Entity(tableName = "channel_snapshots")
data class ChannelSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val recordedAt: Long,
    val subscriberCount: Long?,
    val viewCount: Long,
    val videoCount: Long
)
