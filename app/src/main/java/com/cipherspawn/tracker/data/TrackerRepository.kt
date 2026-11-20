package com.cipherspawn.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LikesSummary(val total: Long, val status: String)

class TrackerRepository(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()

    suspend fun fetchChannel(input: String): ChannelStats = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your YouTube Data API key in Settings first." }
        val parsed = parseChannelInput(input)
        val selector = if (parsed.startsWith("UC")) "id=$parsed" else "forHandle=$parsed"
        val data = getJson("channels?part=snippet,statistics,contentDetails&$selector")
        val item = data.optJSONArray("items")?.optJSONObject(0)
            ?: throw IllegalArgumentException("No public YouTube channel matched that input.")
        val stats = item.optJSONObject("statistics") ?: JSONObject()
        val snippet = item.optJSONObject("snippet") ?: JSONObject()
        val hidden = stats.optBoolean("hiddenSubscriberCount", false)
        ChannelStats(
            channelId = item.getString("id"), title = snippet.optString("title", "YouTube channel"),
            handle = snippet.optString("customUrl").takeIf { it.isNotBlank() && it != "null" },
            subscriberCount = if (hidden || !stats.has("subscriberCount")) null else stats.optLong("subscriberCount"),
            videoCount = stats.optLong("videoCount"), viewCount = stats.optLong("viewCount"),
            likesTotal = null, likesStatus = "Public likes scan queued",
            updatedAt = java.time.Instant.now().toString()
        )
    }

    suspend fun fetchLikes(channelId: String): LikesSummary = withContext(Dispatchers.IO) {
        val channel = getJson("channels?part=contentDetails&id=$channelId")
            .optJSONArray("items")?.optJSONObject(0)
            ?: throw IllegalArgumentException("Could not read this channel's public uploads.")
        val uploads = channel.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
        if (uploads.isBlank()) throw IllegalArgumentException("No public uploads playlist is available.")
        var token: String? = null
        var checked = 0
        var withLikes = 0
        var total = 0L
        var complete = false
        while (checked < 100) {
            val tokenParam = token?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
            val page = getJson("playlistItems?part=contentDetails&playlistId=$uploads&maxResults=50$tokenParam")
            val items = page.optJSONArray("items") ?: org.json.JSONArray()
            val ids = (0 until items.length()).mapNotNull {
                items.optJSONObject(it)?.optJSONObject("contentDetails")?.optString("videoId")?.takeIf { id -> !id.isNullOrBlank() && id != "null" }
            }
            if (ids.isNotEmpty()) {
                val videos = getJson("videos?part=statistics&id=" + ids.joinToString(",")).optJSONArray("items") ?: org.json.JSONArray()
                for (i in 0 until videos.length()) {
                    val stats = videos.optJSONObject(i)?.optJSONObject("statistics")
                    if (stats?.has("likeCount") == true) { total += stats.optLong("likeCount"); withLikes++ }
                }
                checked += ids.size
            }
            token = page.optString("nextPageToken").takeIf { it.isNotBlank() && it != "null" }
            if (token == null) { complete = true; break }
            if (ids.isEmpty()) break
        }
        val status = (if (complete) "All public uploads checked" else "Partial scan (up to 100 videos)") +
            " · $checked videos checked · $withLikes with public like counts"
        LikesSummary(total, status)
    }

    private fun getJson(path: String): JSONObject {
        val url = "https://www.googleapis.com/youtube/v3/$path&key=" + URLEncoder.encode(apiKey, "UTF-8")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (response.isSuccessful) return json
            val apiMessage = json.optJSONObject("error")?.optString("message")
            val message = when (response.code) {
                400, 403 -> apiMessage ?: "YouTube rejected this API key. Check that YouTube Data API v3 is enabled."
                404 -> "YouTube channel or resource not found."
                429 -> "YouTube API quota exceeded. Try again later."
                else -> apiMessage ?: "YouTube request failed (HTTP ${response.code})."
            }
            throw IOException(message)
        }
    }

    private fun parseChannelInput(input: String): String {
        val value = input.trim()
        Regex("(UC[a-zA-Z0-9_-]{20,})").find(value)?.let { return it.value }
        Regex("(?:youtube\\.com/)?@([a-zA-Z0-9._-]+)", RegexOption.IGNORE_CASE)
            .find(value)?.let { return it.groupValues[1] }
        if (value.matches(Regex("@[a-zA-Z0-9._-]+"))) return value.drop(1)
        throw IllegalArgumentException("Enter a YouTube channel URL, @handle, or UC channel ID.")
    }
}
