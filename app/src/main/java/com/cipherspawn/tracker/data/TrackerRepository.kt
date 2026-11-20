package com.cipherspawn.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LikesSummary(val total: Long, val status: String)

class TrackerRepository(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetchChannel(input: String): ChannelStats = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://")) { "Set a valid HTTPS Worker URL in Settings first." }
        val encoded = java.net.URLEncoder.encode(input.trim(), "UTF-8")
        val data = getJson("/api/channel?channel=$encoded")
        ChannelStats(
            channelId = data.getString("channelId"),
            title = data.getString("title"),
            handle = data.optString("handle").takeIf { it.isNotBlank() && it != "null" },
            subscriberCount = if (data.has("subscriberCount") && !data.isNull("subscriberCount")) data.getLong("subscriberCount") else null,
            videoCount = data.optLong("videoCount"),
            viewCount = data.optLong("viewCount"),
            likesTotal = null,
            likesStatus = "Public likes scan queued",
            updatedAt = data.optString("updatedAt", "Just now")
        )
    }

    suspend fun fetchLikes(channelId: String): LikesSummary = withContext(Dispatchers.IO) {
        val data = getJson("/api/likes?channelId=$channelId&maxVideos=100")
        val listed = data.optInt("videosListed")
        val withLikes = data.optInt("videosWithLikeCount")
        val status = buildString {
            append(if (data.optBoolean("complete")) "All public uploads checked" else "Partial scan")
            append(" · $listed videos checked · $withLikes with public like counts")
        }
        LikesSummary(data.optLong("likesTotal"), status)
    }

    private fun getJson(path: String): JSONObject {
        var lastRetryable: IOException? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder().url(baseUrl.trimEnd('/') + path).get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) return JSONObject(body)
                    val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP ${response.code})."
                    if (response.code == 429 || response.code >= 500) {
                        lastRetryable = IOException(message)
                    } else {
                        throw IllegalArgumentException(message)
                    }
                }
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: IOException) {
                lastRetryable = e
            }
            if (attempt < 2) Thread.sleep(400L * (attempt + 1))
        }
        throw lastRetryable ?: IOException("Network request failed.")
    }
}
