package com.cipherspawn.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TrackerRepository(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetchChannel(input: String): ChannelStats = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://")) { "Set your HTTPS Worker URL in Settings first." }
        val encoded = java.net.URLEncoder.encode(input.trim(), "UTF-8")
        val channelJson = getJson("/api/channel?channel=$encoded")
        val likesJson = runCatching {
            getJson("/api/likes?channelId=${channelJson.getString("channelId")}&maxVideos=500")
        }.getOrNull()

        ChannelStats(
            channelId = channelJson.getString("channelId"),
            title = channelJson.getString("title"),
            handle = channelJson.optString("handle").takeIf { it.isNotBlank() && it != "null" },
            subscriberCount = if (channelJson.has("subscriberCount") && !channelJson.isNull("subscriberCount")) channelJson.getLong("subscriberCount") else null,
            videoCount = channelJson.optLong("videoCount"),
            viewCount = channelJson.optLong("viewCount"),
            likesTotal = likesJson?.optLong("likesTotal"),
            likesStatus = likesJson?.let {
                val scanned = it.optInt("scannedVideos")
                if (it.optBoolean("complete")) "All public videos scanned ($scanned)"
                else "Partial scan: $scanned videos"
            } ?: "Likes scan unavailable; channel stats are still loaded",
            updatedAt = channelJson.optString("updatedAt", "Just now")
        )
    }

    private fun getJson(path: String): JSONObject {
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder().url(baseUrl.trimEnd('/') + path).get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        if (json.optBoolean("errorFlag")) throw IOException(json.optString("error"))
                        return json
                    }
                    if (response.code == 429 || response.code >= 500) {
                        lastError = IOException("Server is busy (HTTP ${response.code}). Try again shortly.")
                    } else {
                        val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                        throw IOException(message?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP ${response.code}).")
                    }
                }
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < 2) Thread.sleep(500L * (attempt + 1))
        }
        throw lastError ?: IOException("Network request failed.")
    }
}
