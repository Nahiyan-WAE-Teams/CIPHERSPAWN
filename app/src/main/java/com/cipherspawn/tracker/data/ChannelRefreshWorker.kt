package com.cipherspawn.tracker.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cipherspawn.tracker.data.TrackerDatabase
import com.cipherspawn.tracker.data.TrackerRepository

class ChannelRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = SecurePrefs.get(applicationContext)
        val apiKey = prefs.getString("youtube_api_key", "").orEmpty()
        if (apiKey.isBlank()) return Result.success()

        val dao = TrackerDatabase.get(applicationContext).trackerDao()
        val tracked = dao.getAllTracked()
        if (tracked.isEmpty()) return Result.success()

        var failures = 0
        for (saved in tracked) {
            try {
                val latest = TrackerRepository(apiKey).fetchChannel(saved.channelId)
                dao.saveTracked(
                    TrackedChannel(
                        channelId = latest.channelId,
                        title = latest.title,
                        handle = latest.handle,
                        subscriberCount = latest.subscriberCount,
                        videoCount = latest.videoCount,
                        viewCount = latest.viewCount,
                        likesTotal = saved.likesTotal,
                        likesStatus = saved.likesStatus,
                        updatedAt = latest.updatedAt,
                        thumbnailUrl = saved.thumbnailUrl
                    )
                )
                dao.addSnapshot(
                    ChannelSnapshot(
                        channelId = latest.channelId,
                        recordedAt = System.currentTimeMillis(),
                        subscriberCount = latest.subscriberCount,
                        viewCount = latest.viewCount,
                        videoCount = latest.videoCount
                    )
                )
            } catch (_: Exception) {
                failures += 1
            }
        }
        return if (failures > 0) Result.retry() else Result.success()
    }
}
