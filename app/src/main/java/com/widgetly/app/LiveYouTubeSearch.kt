package com.widgetly.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YouTubeSearchPage(val items: List<YouTubeSearchResult>, val continuation: String?)

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val creator: String,
    val duration: String,
    val isMusic: Boolean,
    val isChannel: Boolean = false,
    val channelId: String = "",
    val isCollection: Boolean = false,
    val collectionId: String = "",
    val description: String = "",
    val videoCount: String = "",
    val thumbnailUrl: String = ""
)

object LiveYouTubeSearch {
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Throws(IOException::class)
    fun search(query: String, music: Boolean = false, channels: Boolean = false, playlists: Boolean = false): List<YouTubeSearchResult> =
        searchPage(query, music, channels, playlists).items

    @Throws(IOException::class)
    fun searchPage(query: String, music: Boolean = false, channels: Boolean = false, playlists: Boolean = false): YouTubeSearchPage {
        require(query.isNotBlank()) { "Enter something to search for." }
        val body = searchContext(music)
            .put("query", query)
            .apply {
                if (!music) {
                    put("params", when {
                        channels -> "EgIQAg=="
                        playlists -> "EgIQAw=="
                        else -> "EgIQAQ=="
                    })
                }
            }
        val primary = executeSearch(body, music, channels, playlists)
        if (music) return primary
        // Merge an unfiltered pass to recover results hidden by YouTube's narrow filter params.
        val fallback = searchContext(false).put("query", query)
        val broad = executeSearch(fallback, false, channels, playlists)
        return YouTubeSearchPage((primary.items + broad.items).distinctBy { resultKey(it) }.take(40), broad.continuation ?: primary.continuation)
    }

    @Throws(IOException::class)
    fun continueSearch(
        continuation: String,
        music: Boolean = false,
        channels: Boolean = false,
        playlists: Boolean = false
    ): YouTubeSearchPage {
        require(continuation.isNotBlank()) { "No more search results are available." }
        val body = searchContext(music).put("continuation", continuation)
        return executeSearch(body, music, channels, playlists)
    }

    private fun searchContext(music: Boolean): JSONObject =
        JSONObject().put("context", JSONObject().put("client", JSONObject()
            .put("clientName", if (music) "WEB_REMIX" else "WEB")
            .put("clientVersion", if (music) "1.20261119.01.00" else "2.20261119.01.00")))

    @Throws(IOException::class)
    private fun executeSearch(
        body: JSONObject,
        music: Boolean,
        channels: Boolean,
        playlists: Boolean
    ): YouTubeSearchPage {
        val request = Request.Builder()
            .url(if (music) "https://music.youtube.com/youtubei/v1/search" else "https://www.youtube.com/youtubei/v1/search")
            .post(body.toString().toRequestBody(jsonType))
            .header("User-Agent", USER_AGENT)
            .header("Origin", if (music) "https://music.youtube.com" else "https://www.youtube.com")
            .header("Referer", if (music) "https://music.youtube.com/" else "https://www.youtube.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube search failed (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val found = LinkedHashMap<String, YouTubeSearchResult>()
            collect(root, found, music, channels, playlists)
            return YouTubeSearchPage(found.values.take(40), findContinuation(root))
        }
    }
    private fun collect(value: Any?, out: MutableMap<String, YouTubeSearchResult>, music: Boolean, channels: Boolean, playlists: Boolean) {
        when (value) {
            is JSONObject -> {
                val video = value.optJSONObject("videoRenderer")
                    ?: value.optJSONObject("gridVideoRenderer")
                    ?: value.optJSONObject("compactVideoRenderer")
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

    private fun resultKey(result: YouTubeSearchResult): String = when {
        result.isChannel -> "channel:" + result.channelId
        result.isCollection -> "collection:" + result.collectionId
        else -> result.videoId
    }

    private fun addVideo(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>, music: Boolean) {
        val id = item.optString("videoId")
        val title = text(item.optJSONObject("title"))
        if (id.isBlank() || title.isBlank()) return
        val owner = text(item.optJSONObject("ownerText")).ifBlank { text(item.optJSONObject("shortBylineText")) }.ifBlank { "YouTube creator" }
        val duration = text(item.optJSONObject("lengthText"))
        out.putIfAbsent(id, YouTubeSearchResult(id, title, owner, duration, music))
    }

    private fun addChannel(item: JSONObject, out: MutableMap<String, YouTubeSearchResult>) {
        val id = item.optString("channelId")
        val title = text(item.optJSONObject("title"))
        if (id.isBlank() || title.isBlank()) return
        val subscribers = text(item.optJSONObject("subscriberCountText"))
        val description = text(item.optJSONObject("descriptionSnippet")).ifBlank { text(item.optJSONObject("description")) }
        val videoCount = text(item.optJSONObject("videoCountText"))
        val thumbnail = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { images ->
            images.optJSONObject(images.length() - 1)?.optString("url").orEmpty()
        }.orEmpty()
        out.putIfAbsent("channel:$id", YouTubeSearchResult("", title, subscribers, videoCount, false, true, id, description = description, videoCount = videoCount, thumbnailUrl = thumbnail))
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
            var nextContinuation: String? = null
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
                nextContinuation = findContinuation(root)
            }
            if (nextContinuation.isNullOrBlank() || nextContinuation == continuation || found.size >= 1000) break
            continuation = nextContinuation
        }
        return found.values.filter { it.videoId.isNotBlank() }.take(1000)
    }

    @Throws(IOException::class)
    fun latestUploads(channelId: String): List<YouTubeSearchResult> {
        require(channelId.startsWith("UC")) { "Invalid YouTube channel ID." }
        val channelRoot = browseChannel(channelId)
        val videoTab = findChannelVideosTarget(channelRoot)
        val firstPage = if (videoTab == null) channelRoot else
            runCatching { browseChannel(videoTab.first, videoTab.second) }.getOrDefault(channelRoot)
        val found = LinkedHashMap<String, YouTubeSearchResult>()
        collect(firstPage, found, false, false, false)
        var continuation = findContinuation(firstPage)
        var pageCount = 0
        while (!continuation.isNullOrBlank() && pageCount < 4 && found.size < 120) {
            val page = browseChannel(continuation = continuation)
            collect(page, found, false, false, false)
            val next = findContinuation(page)
            if (next.isNullOrBlank() || next == continuation) break
            continuation = next
            pageCount++
        }
        return found.values.filter { it.videoId.isNotBlank() }.take(100)
    }

    private fun browseChannel(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): JSONObject {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", "2.20261119.01.00")))
            .apply {
                if (continuation.isNullOrBlank()) {
                    put("browseId", browseId)
                    if (!params.isNullOrBlank()) put("params", params)
                } else {
                    put("continuation", continuation)
                }
            }
            .toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/browse")
            .post(body.toRequestBody(jsonType))
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not load channel uploads.")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun findChannelVideosTarget(value: Any?): Pair<String, String?>? {
        when (value) {
            is JSONObject -> {
                val tab = value.optJSONObject("tabRenderer")
                if (tab != null) {
                    val title = text(tab.optJSONObject("title")).trim().lowercase()
                    if (title == "videos" || title == "uploads") {
                        val endpoint = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                            ?: tab.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                        val browseId = endpoint?.optString("browseId").orEmpty()
                        if (browseId.isNotBlank()) {
                            val params = endpoint?.optString("params")?.takeIf { it.isNotBlank() }
                            return browseId to params
                        }
                    }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    findChannelVideosTarget(value.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                findChannelVideosTarget(value.opt(index))?.let { return it }
            }
        }
        return null
    }
    @Throws(IOException::class)
    fun browseMusicCollection(browseId: String): List<YouTubeSearchResult> {
        require(browseId.isNotBlank()) { "Invalid music collection." }
        val found = LinkedHashMap<String, YouTubeSearchResult>()
        var continuation: String? = null
        for (page in 0 until 10) {
            var nextContinuation: String? = null
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
                nextContinuation = findContinuation(root)
            }
            if (nextContinuation.isNullOrBlank() || nextContinuation == continuation || found.size >= 1000) break
            continuation = nextContinuation
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
