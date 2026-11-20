package com.cipherspawn.tracker.data

data class ChannelStats(
    val channelId: String,
    val title: String,
    val handle: String?,
    val subscriberCount: Long?,
    val videoCount: Long,
    val viewCount: Long,
    val likesTotal: Long?,
    val likesStatus: String?,
    val updatedAt: String
)
