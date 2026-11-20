package com.widgetly.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val creator: String,
    val duration: String,
    val isMusic: Boolean,
    val isChannel: Boolean = false,
    val channelId: String = ""
)

object LiveYouTubeSearch {
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Throws(IOException::class)
    fun search(query: String, music: Boolean = false, channels: Boolean = false): List<YouTubeSearchResult> {
        require(query.isNotBlank()) { "Enter something to search for." }
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", "2.20261119.01.00")))
            .put("query", query)
            .toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search")
            .post(body.toRequestBody(jsonType))
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube search failed (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val found = LinkedHashMap<String, YouTubeSearchResult>()
            collect(root, found, music, channels)
            return found.values.take(40)
        }
    }

    private fun collect(value: Any?, out: MutableMap<String, YouTubeSearchResult>, music: Boolean, channels: Boolean) {
        when (value) {
            is JSONObject -> {
                val video = value.optJSONObject("videoRenderer")
                if (video != null && !music) addVideo(video, out, false)
                val musicItem = value.optJSONObject("musicResponsiveListItemRenderer")
                if (musicItem != null && music) addMusic(musicItem, out)
                val channel = value.optJSONObject("channelRenderer")
                if (channel != null && channels) addChannel(channel, out)
                val keys = value.keys()
                while (keys.hasNext()) collect(value.opt(keys.next()), out, music, channels)
            }
            is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i), out, music, channels)
        }
    }

    private fun addVideo(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>, music: Boolean) {
        val id = item.optString("videoId")
        val title = text(item.optJSONObject("title"))
        if (id.isBlank() || title.isBlank()) return
        val owner = text(item.optJSONObject("ownerText")).ifBlank { "YouTube creator" }
        val duration = text(item.optJSONObject("lengthText"))
        out.putIfAbsent(id, YouTubeSearchResult(id, title, owner, duration, music))
    }

    private fun addChannel(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>) {
        val id = item.optString("channelId")
        val title = text(item.optJSONObject("title"))
        if (id.isBlank() || title.isBlank()) return
        val subscribers = text(item.optJSONObject("subscriberCountText"))
        out.putIfAbsent("channel:$id", YouTubeSearchResult("", title, subscribers, "", false, true, id))
    }

    @Throws(IOException::class)
    fun latestUploads(channelId: String): List<YouTubeSearchResult> {
        require(channelId.startsWith("UC")) { "Invalid YouTube channel ID." }
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", "2.20261119.01.00")))
            .put("browseId", channelId)
            .toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/browse")
            .post(body.toRequestBody(jsonType))
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not load channel uploads (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val found = LinkedHashMap<String, YouTubeSearchResult>()
            collect(root, found, false, false)
            return found.values.take(30)
        }
    }

    private fun addMusic(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>) {
        val id = item.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            .ifBlank { item.optString("videoId") }
        val title = runs(item.optJSONArray("flexColumns")).ifBlank { text(item.optJSONObject("title")) }
        if (id.isBlank() || title.isBlank()) return
        val creator = runs(item.optJSONArray("fixedColumns")).ifBlank { "YouTube Music" }
        out.putIfAbsent(id, YouTubeSearchResult(id, title, creator, "", true))
    }

    private fun runs(array: JSONArray?): String {
        if (array == null) return ""
        val parts = ArrayList<String>()
        for (i in 0 until array.length()) {
            val renderer = array.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val text = renderer?.optJSONObject("text")
            val value = text(text)
            if (value.isNotBlank()) parts += value
        }
        return parts.firstOrNull().orEmpty()
    }

    private fun text(value: JSONObject?): String {
        if (value == null) return ""
        value.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = value.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) {
                if (i > 0) append("")
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"
}
