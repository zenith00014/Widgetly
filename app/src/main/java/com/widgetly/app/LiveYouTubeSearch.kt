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
    val channelId: String = "",
    val isCollection: Boolean = false,
    val collectionId: String = ""
)

object LiveYouTubeSearch {
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Throws(IOException::class)
    fun search(query: String, music: Boolean = false, channels: Boolean = false, playlists: Boolean = false): List<YouTubeSearchResult> {
        require(query.isNotBlank()) { "Enter something to search for." }
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", if (music) "WEB_REMIX" else "WEB")
                .put("clientVersion", if (music) "1.20261119.01.00" else "2.20261119.01.00")))
            .put("query", query)
            .toString()
        val request = Request.Builder()
            .url(if (music) "https://music.youtube.com/youtubei/v1/search" else "https://www.youtube.com/youtubei/v1/search")
            .post(body.toRequestBody(jsonType))
            .header("User-Agent", USER_AGENT)
            .header("Origin", if (music) "https://music.youtube.com" else "https://www.youtube.com")
            .header("Referer", if (music) "https://music.youtube.com/" else "https://www.youtube.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube search failed (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val found = LinkedHashMap<String, YouTubeSearchResult>()
            collect(root, found, music, channels, playlists)
            return found.values.take(40)
        }
    }

    private fun collect(value: Any?, out: MutableMap<String, YouTubeSearchResult>, music: Boolean, channels: Boolean, playlists: Boolean) {
        when (value) {
            is JSONObject -> {
                val video = value.optJSONObject("videoRenderer")
                if (video != null && !music && !channels && !playlists) addVideo(video, out, false)
                val musicItem = value.optJSONObject("musicResponsiveListItemRenderer")
                if (musicItem != null && music) addMusic(musicItem, out)
                val channel = value.optJSONObject("channelRenderer")
                if (channel != null && channels) addChannel(channel, out)
                val playlist = value.optJSONObject("playlistRenderer")
                    ?: value.optJSONObject("compactPlaylistRenderer")
                if (playlist != null && playlists) addPlaylist(playlist, out)
                val playlistVideo = value.optJSONObject("playlistVideoRenderer")
                if (playlistVideo != null && !music && !channels && !playlists) addPlaylistVideo(playlistVideo, out)
                val keys = value.keys()
                while (keys.hasNext()) collect(value.opt(keys.next()), out, music, channels, playlists)
            }
            is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i), out, music, channels, playlists)
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

    private fun addPlaylist(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>) {
        val playlistId = item.optString("playlistId")
        val title = text(item.optJSONObject("title"))
        if (playlistId.isBlank() || title.isBlank()) return
        val owner = text(item.optJSONObject("shortBylineText")).ifBlank {
            text(item.optJSONObject("longBylineText"))
        }.ifBlank { "YouTube playlist" }
        out.putIfAbsent(
            "playlist:$playlistId",
            YouTubeSearchResult(
                videoId = "",
                title = title,
                creator = owner,
                duration = text(item.optJSONObject("videoCountText")),
                isMusic = false,
                isCollection = true,
                collectionId = playlistId
            )
        )
    }

    private fun addPlaylistVideo(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>) {
        val id = item.optString("videoId")
        val title = text(item.optJSONObject("title"))
        if (id.isBlank() || title.isBlank()) return
        val creator = text(item.optJSONObject("shortBylineText"))
            .ifBlank { text(item.optJSONObject("longBylineText")) }
            .ifBlank { "YouTube creator" }
        val duration = text(item.optJSONObject("lengthText"))
        out.putIfAbsent(id, YouTubeSearchResult(id, title, creator, duration, false))
    }

    @Throws(IOException::class)
    fun browseVideoPlaylist(playlistId: String): List<YouTubeSearchResult> {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val found = LinkedHashMap<String, YouTubeSearchResult>()
        var continuation: String? = null
        for (page in 0 until 10) {
            val body = JSONObject()
                .put("context", JSONObject().put("client", JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", "2.20261119.01.00")))
                .apply { if (continuation == null) put("browseId", browseId) else put("continuation", continuation) }
                .toString()
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/browse")
                .post(body.toRequestBody(jsonType))
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Could not load YouTube playlist (HTTP ${response.code}).")
                val root = JSONObject(response.body?.string().orEmpty())
                collect(root, found, false, false, false)
                continuation = findContinuation(root)
            }
            if (continuation.isNullOrBlank() || found.size >= 1000) break
        }
        return found.values.filter { it.videoId.isNotBlank() }.take(1000)
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
            collect(root, found, false, false, false)
            return found.values.take(30)
        }
    }

    @Throws(IOException::class)
    fun browseMusicCollection(browseId: String): List<YouTubeSearchResult> {
        require(browseId.isNotBlank()) { "Invalid music collection." }
        val found = LinkedHashMap<String, YouTubeSearchResult>()
        var continuation: String? = null
        repeat(10) {
            val body = JSONObject()
                .put("context", JSONObject().put("client", JSONObject()
                    .put("clientName", "WEB_REMIX")
                    .put("clientVersion", "1.20261119.01.00")))
                .apply { if (continuation == null) put("browseId", browseId) else put("continuation", continuation) }
                .toString()
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse")
                .post(body.toRequestBody(jsonType))
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Could not load music collection (HTTP ${response.code}).")
                val root = JSONObject(response.body?.string().orEmpty())
                collect(root, found, true, false, false)
                continuation = findContinuation(root)
            }
            if (continuation.isNullOrBlank() || found.size >= 1000) return@repeat
        }
        return found.values.filter { it.videoId.isNotBlank() }.take(1000)
    }

    private fun findContinuation(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                value.optJSONObject("nextContinuationData")?.optString("continuation")
                    ?.takeIf { it.isNotBlank() }?.let { return it }
                value.optJSONObject("continuationCommand")?.optString("token")
                    ?.takeIf { it.isNotBlank() }?.let { return it }
                val keys = value.keys()
                while (keys.hasNext()) findContinuation(value.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                findContinuation(value.opt(index))?.let { return it }
            }
        }
        return null
    }

    private fun addMusic(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>) {
        val flex = columnTexts(item.optJSONArray("flexColumns"))
        val fixed = columnTexts(item.optJSONArray("fixedColumns"))
        val firstRun = item.optJSONArray("flexColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
        val firstEndpoint = firstRun?.optJSONObject("navigationEndpoint")
        val browseId = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId").orEmpty()
            .ifBlank { firstEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty() }
        val id = item.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            .ifBlank { firstEndpoint?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty() }
            .ifBlank { item.optString("videoId") }
        val title = flex.firstOrNull().orEmpty().ifBlank { text(item.optJSONObject("title")) }
        if (title.isBlank()) return
        val creator = flex.getOrNull(1).orEmpty().ifBlank { "YouTube Music" }
        val duration = fixed.firstOrNull().orEmpty()
        if (id.isNotBlank()) {
            out.putIfAbsent(id, YouTubeSearchResult(id, title, creator, duration, true))
        } else if (browseId.isNotBlank()) {
            out.putIfAbsent(
                "collection:$browseId",
                YouTubeSearchResult(
                    videoId = "",
                    title = title,
                    creator = creator,
                    duration = "",
                    isMusic = true,
                    isCollection = true,
                    collectionId = browseId
                )
            )
        }
    }

    private fun columnTexts(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val renderer = array.optJSONObject(index)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            text(renderer?.optJSONObject("text")).takeIf { it.isNotBlank() }
        }
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
