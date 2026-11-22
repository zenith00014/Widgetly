package com.widgetly.app

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RushWebBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    @Volatile private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    @JavascriptInterface
    fun searchMusic(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        activity.lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, music = true).filter { it.videoId.isNotBlank() }.take(30)
                }
                val payload = JSONArray().apply {
                    results.forEach { item ->
                        put(JSONObject()
                            .put("id", item.videoId)
                            .put("title", item.title)
                            .put("artist", item.creator)
                            .put("duration", item.duration)
                            .put("thumbnail", thumbnailFor(item.videoId, item.thumbnailUrl)))
                    }
                }
                publish("rushMusicSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushMusicSearchError", JSONObject()
                    .put("message", error.message ?: "Music search failed.").toString())
            }
        }
    }

    @JavascriptInterface
    fun playMusic(videoId: String, title: String, artist: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            publish("rushMusicPlaybackState", JSONObject().put("state", "loading").put("title", title).toString())
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val quality = prefs.getInt("audio_quality", 0).takeIf { it > 0 } ?: 160
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = true, maxAudioBitrate = quality)
                }
                val controller = getController()
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                    .setExtras(PlaybackMetadata.extras(videoId, true, quality, false))
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(resolved.url)
                    .setMimeType(resolved.mimeType)
                    .setMediaMetadata(metadata)
                    .build()
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
                val history = runCatching { JSONArray(prefs.getString("music_history", "[]") ?: "[]") }.getOrDefault(JSONArray())
                val updatedHistory = JSONArray().put(JSONObject()
                    .put("id", videoId).put("title", title).put("artist", artist).put("thumbnail", thumbnail))
                for (index in 0 until history.length()) {
                    val entry = history.optJSONObject(index) ?: continue
                    if (entry.optString("id") != videoId && updatedHistory.length() < 50) updatedHistory.put(entry)
                }
                prefs.edit()
                    .putString("music_last_id", videoId)
                    .putString("music_last_title", title)
                    .putString("music_last_artist", artist)
                    .putString("music_last_thumbnail", thumbnail)
                    .putString("music_history", updatedHistory.toString())
                    .apply()
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", "playing").put("id", videoId)
                    .put("title", title).put("artist", artist).put("thumbnail", thumbnail).toString())
            } catch (error: Exception) {
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", "error")
                    .put("message", error.message ?: "Could not play this track.").toString())
            }
        }
    }

    @JavascriptInterface
    fun queueMusic(videoId: String, title: String, artist: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val quality = prefs.getInt("audio_quality", 0).takeIf { it > 0 } ?: 160
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = true, maxAudioBitrate = quality)
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(title).setArtist(artist)
                    .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                    .setExtras(PlaybackMetadata.extras(videoId, true, quality, false))
                    .build()
                val item = MediaItem.Builder().setMediaId(videoId).setUri(resolved.url)
                    .setMimeType(resolved.mimeType).setMediaMetadata(metadata).build()
                val controller = getController()
                controller.addMediaItem(item)
                if (controller.mediaItemCount == 1) {
                    controller.prepare()
                    controller.play()
                }
                publish("rushMusicQueued", JSONObject().put("title", title).toString())
            } catch (error: Exception) {
                publish("rushMusicSearchError", JSONObject().put("message", error.message ?: "Could not add this track to the queue.").toString())
            }
        }
    }

    @JavascriptInterface
    fun searchVideos(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        activity.lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, music = false).filter { it.videoId.isNotBlank() }.take(30)
                }
                val payload = JSONArray().apply {
                    results.forEach { item ->
                        put(JSONObject().put("id", item.videoId).put("title", item.title)
                            .put("artist", item.creator).put("duration", item.duration)
                            .put("thumbnail", thumbnailFor(item.videoId, item.thumbnailUrl)))
                    }
                }
                publish("rushVideoSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushVideoSearchError", JSONObject()
                    .put("message", playbackErrorMessage(error, "Video search failed.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun playVideo(videoId: String, title: String, creator: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            publish("rushVideoPlaybackState", JSONObject().put("state", "loading").put("title", title).toString())
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val quality = prefs.getInt("video_quality", 0)
                val preferMp4 = prefs.getBoolean("download_mp4", false)
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = false, maxHeight = quality.takeIf { it > 0 },
                        preferMp4 = preferMp4, preferDash = !preferMp4, allowDashFallback = !preferMp4)
                }
                val controller = getController()
                val subtitleConfigs = resolved.subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mimeType).setLanguage(subtitle.languageTag)
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .setLabel(subtitle.label).build()
                }
                val metadata = MediaMetadata.Builder().setTitle(title).setArtist(creator)
                    .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                    .setExtras(PlaybackMetadata.extras(videoId, false, quality, preferMp4)).build()
                val item = MediaItem.Builder().setMediaId(videoId).setUri(resolved.url)
                    .setMimeType(resolved.mimeType).setMediaMetadata(metadata)
                    .setSubtitleConfigurations(subtitleConfigs).build()
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
                activity.runOnUiThread { activity.showNativeVideo(controller) }
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        val lines = cueGroup.cues.mapNotNull { it.text?.toString()?.takeIf { it.isNotBlank() } }
                        publish("rushVideoCaptions", JSONArray(lines).toString())
                    }
                }
                controller.addListener(listener)
                publish("rushVideoPlaybackState", JSONObject().put("state", "playing")
                    .put("title", title).put("creator", creator).put("captionTracks", subtitleConfigs.size).toString())
            } catch (error: Exception) {
                publish("rushVideoPlaybackState", JSONObject().put("state", "error")
                    .put("message", playbackErrorMessage(error, "Could not play this video.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun hideVideo() {
        activity.hideNativeVideo()
    }

    @JavascriptInterface
    fun loadLyrics(title: String, artist: String) {
        val track = title.trim()
        val performer = artist.trim()
        if (track.isEmpty()) return
        activity.lifecycleScope.launch {
            publish("rushMusicLyricsLoading", JSONObject().toString())
            try {
                val lyrics = withContext(Dispatchers.IO) {
                    val url = okhttp3.HttpUrl.Builder().scheme("https").host("lrclib.net").addPathSegments("api/search")
                        .addQueryParameter("track_name", track)
                        .apply { if (performer.isNotBlank()) addQueryParameter("artist_name", performer) }
                        .build()
                    val request = okhttp3.Request.Builder().url(url).header("User-Agent", "Widgetly/1.0").build()
                    WidgetlyNetwork.newBuilder().callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                        .build().newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw java.io.IOException("Lyrics are unavailable for this track.")
                            val results = JSONArray(response.body?.string().orEmpty())
                            var best: JSONObject? = null
                            for (i in 0 until results.length()) {
                                val candidate = results.optJSONObject(i) ?: continue
                                if (candidate.optString("syncedLyrics").isNotBlank()) { best = candidate; break }
                                if (best == null && candidate.optString("plainLyrics").isNotBlank()) best = candidate
                            }
                            best ?: throw java.io.IOException("No lyrics are available for this track.")
                        }
                }
                publish("rushMusicLyrics", JSONObject().put("synced", lyrics.optString("syncedLyrics"))
                    .put("plain", lyrics.optString("plainLyrics")).put("title", track).put("artist", performer).toString())
            } catch (error: Exception) {
                publish("rushMusicLyricsError", JSONObject().put("message", error.message ?: "Lyrics are unavailable.").toString())
            }
        }
    }

    @JavascriptInterface
    fun musicPosition(): Long = runCatching { controllerFuture?.get()?.currentPosition ?: 0L }.getOrDefault(0L)

    @JavascriptInterface
    fun loadMusicHistory() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        publish("rushMusicHistory", prefs.getString("music_history", "[]") ?: "[]")
    }

    @JavascriptInterface
    fun loadNews(category: String) {
        val query = when (category.trim().lowercase()) {
            "world" -> "world news"
            "technology" -> "technology news"
            "sports" -> "sports news"
            "entertainment" -> "entertainment news"
            "culture" -> "culture news"
            else -> "latest news"
        }
        fetchNews(query)
    }

    @JavascriptInterface
    fun searchNews(query: String) {
        val term = query.trim()
        if (term.isNotEmpty()) fetchNews(term)
    }

    @JavascriptInterface
    fun openNews(url: String) {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        if (uri.scheme != "https") return
        activity.runOnUiThread {
            runCatching {
                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }.onFailure {
                publish("rushNewsError", JSONObject().put("message", "No browser is available to open this article.").toString())
            }
        }
    }

    private fun fetchNews(query: String) {
        activity.lifecycleScope.launch {
            val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
            val cacheKey = "news_cache_" + query.trim().lowercase().replace(Regex("\\s+"), "_")
            val cached = prefs.getString(cacheKey, null)
            if (!cached.isNullOrBlank()) runCatching { JSONArray(cached) }.getOrNull()?.let { publish("rushNewsResults", it.toString()) }
            else publish("rushNewsLoading", JSONObject().put("query", query).toString())
            try {
                val articles = withContext(Dispatchers.IO) { requestNews(query) }
                prefs.edit().putString(cacheKey, articles.toString()).apply()
                publish("rushNewsResults", articles.toString())
            } catch (error: Exception) {
                if (cached.isNullOrBlank()) publish("rushNewsError", JSONObject().put("message", playbackErrorMessage(error, "Could not load news.")).toString())
            }
        }
    }
    private fun requestNews(query: String): JSONArray {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = okhttp3.Request.Builder()
            .url("https://news.google.com/rss/search?q=$encoded&hl=en&gl=US&ceid=US:en")
            .header("User-Agent", "Widgetly/1.0 (Android)")
            .build()
        val client = WidgetlyNetwork.newBuilder()
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("News feed failed (HTTP ${response.code}).")
            val parser = android.util.Xml.newPullParser()
            parser.setInput(response.body?.byteStream(), null)
            val output = JSONArray()
            var event = parser.eventType
            var insideItem = false
            var field = ""
            var title = ""
            var link = ""
            var date = ""
            var description = ""
            var source = ""
            var thumbnail = ""
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && output.length() < 40) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (parser.name == "item") {
                            insideItem = true
                            title = ""; link = ""; date = ""; description = ""; source = ""; thumbnail = ""
                        } else if (insideItem) {
                            field = parser.name
                            if (parser.name == "media:thumbnail" || parser.name == "media:content" || parser.name == "enclosure") {
                                thumbnail = parser.getAttributeValue(null, "url").orEmpty()
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT,
                    org.xmlpull.v1.XmlPullParser.CDSECT -> if (insideItem) {
                        val value = parser.text.orEmpty()
                        when (field) {
                            "title" -> title += value
                            "link" -> link += value
                            "pubDate" -> date += value
                            "description" -> description += value
                            "source" -> source += value
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (thumbnail.isBlank()) {
                                thumbnail = description.substringAfter("src=\"", "").substringBefore("\"")
                                    .ifBlank { description.substringAfter("src='", "").substringBefore("'") }
                            }
                            if (thumbnail.startsWith("//")) thumbnail = "https:$thumbnail"
                            val cleanSummary = description.replace(Regex("<[^>]+>"), " ")
                                .replace(Regex("\\s+"), " ").trim()
                            if (title.isNotBlank() && link.startsWith("https://")) {
                                output.put(JSONObject()
                                    .put("title", title.trim())
                                    .put("url", link.trim())
                                    .put("date", date.trim())
                                    .put("source", source.trim().ifBlank { "News source" })
                                    .put("summary", cleanSummary.take(280))
                                    .put("thumbnail", thumbnail.takeIf { it.startsWith("https://") }.orEmpty()))
                            }
                            insideItem = false
                        }
                        field = ""
                    }
                }
                event = parser.next()
            }
            return output
        }
    }

    @JavascriptInterface
    fun toggleMusicPlayback() {
        activity.lifecycleScope.launch {
            runCatching {
                val player = getController()
                if (player.isPlaying) player.pause() else player.play()
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", if (player.isPlaying) "playing" else "paused")
                    .put("title", player.mediaMetadata.title?.toString().orEmpty()).toString())
            }
        }
    }

    private suspend fun getController(): MediaController {
        val future = withContext(Dispatchers.Main) {
            controllerFuture ?: run {
                val token = SessionToken(activity, android.content.ComponentName(activity, RushPlaybackService::class.java))
                MediaController.Builder(activity, token).buildAsync().also { controllerFuture = it }
            }
        }
        return withContext(Dispatchers.IO) { future.get() }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun thumbnailFor(videoId: String, candidate: String): String =
        candidate.takeIf { it.startsWith("https://") }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private fun publish(callback: String, json: String) {
        webView.post {
            val safe = JSONObject.quote(json)
            webView.evaluateJavascript("window.$callback && window.$callback(JSON.parse($safe))", null)
        }
    }
}
