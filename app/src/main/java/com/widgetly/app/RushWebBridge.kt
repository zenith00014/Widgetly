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
    private var captionListener: androidx.media3.common.Player.Listener? = null


    @JavascriptInterface
    fun getFeedPreferences(): String =
        FeedPreferences.asJson(activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)).toString()

    @JavascriptInterface
    fun saveFeedTopics(topicsJson: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.saveTopics(prefs, topicsJson) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not save topics.").toString()) }
    }

    @JavascriptInterface
    fun followSource(kind: String, id: String, name: String, productive: Boolean) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.follow(prefs, kind, id, name, productive) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not follow this source.").toString()) }
    }

    @JavascriptInterface
    fun unfollowSource(kind: String, id: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.unfollow(prefs, kind, id) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not unfollow this source.").toString()) }
    }

    @JavascriptInterface
    fun setChannelRecommended(id: String, name: String, enabled: Boolean) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.setProductive(prefs, id, name, enabled) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not update this channel.").toString()) }
    }

    @JavascriptInterface
    fun searchChannels(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        activity.lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, channels = true)
                        .filter { it.isChannel && it.channelId.isNotBlank() }
                        .distinctBy { it.channelId }
                        .take(20)
                }
                val payload = JSONArray().apply {
                    channels.forEach { channel ->
                        put(JSONObject()
                            .put("id", channel.channelId)
                            .put("name", channel.title)
                            .put("subscribers", channel.creator)
                            .put("description", channel.description)
                            .put("thumbnail", channel.thumbnailUrl))
                    }
                }
                publish("rushChannelSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushChannelSearchError", JSONObject()
                    .put("message", playbackErrorMessage(error, "Channel search failed.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun loadHomeFeed(mode: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val sources = FeedPreferences.sources(prefs)
            val topics = FeedPreferences.topics(prefs)
            val videos = LinkedHashMap<String, Pair<Int, JSONObject>>()
            val music = LinkedHashMap<String, JSONObject>()
            val news = LinkedHashMap<String, JSONObject>()
            val following = mode.equals("following", true)
            val selectedChannels = if (following) sources.filter { it.kind == "channel" }
                else sources.filter { it.kind == "channel" && it.productive }
            if (selectedChannels.isEmpty() && !following) {
                publish("rushHomeFeedResults", JSONObject()
                    .put("mode", "forYou").put("videos", JSONArray()).put("music", JSONArray())
                    .put("news", JSONArray()).put("message", "Choose productive YouTube channels in Search to start your For You feed.").toString())
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    selectedChannels.take(10).forEach { source ->
                        runCatching {
                            LiveYouTubeSearch.search(source.name, music = false)
                                .filter { it.videoId.isNotBlank() && (it.creator.equals(source.name, true) || it.creator.contains(source.name, true)) }
                                .forEach { item ->
                                    val score = FeedPreferences.rank(item.title, item.description, topics)
                                    val json = JSONObject().put("id", item.videoId).put("title", item.title)
                                        .put("artist", item.creator).put("duration", item.duration)
                                        .put("thumbnail", item.thumbnailUrl)
                                        .put("sourceId", source.id).put("sourceName", source.name)
                                    val previous = videos[item.videoId]
                                    if (previous == null || score > previous.first) videos[item.videoId] = score to json
                                }
                        }
                    }
                    if (following) {
                        sources.filter { it.kind == "artist" }.take(6).forEach { source ->
                            runCatching {
                                LiveYouTubeSearch.search(source.name, music = true).forEach { item ->
                                    if (item.videoId.isNotBlank()) music.putIfAbsent(item.videoId,
                                        JSONObject().put("id", item.videoId).put("title", item.title)
                                            .put("artist", item.creator).put("duration", item.duration)
                                            .put("thumbnail", item.thumbnailUrl))
                                }
                            }
                        }
                        sources.filter { it.kind == "news" }.take(6).forEach { source ->
                            runCatching {
                                val items = requestNews(source.name)
                                for (index in 0 until items.length()) {
                                    val article = items.optJSONObject(index) ?: continue
                                    val publisher = article.optString("source")
                                    if (publisher.contains(source.name, true) || source.name.contains(publisher, true)) {
                                        news.putIfAbsent(article.optString("url"), article)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
            val rankedChannelGroups = videos.values.sortedByDescending { it.first }
                .groupBy { it.second.optString("sourceId") }
            val orderedVideos = buildList {
                var round = 0
                while (size < 30) {
                    var added = false
                    rankedChannelGroups.values.forEach { channelItems ->
                        val candidate = channelItems.getOrNull(round)
                        if (candidate != null && size < 30) {
                            add(candidate.second)
                            added = true
                        }
                    }
                    if (!added) break
                    round++
                }
            }
            publish("rushHomeFeedResults", JSONObject()
                .put("mode", if (following) "following" else "forYou")
                .put("videos", JSONArray(orderedVideos))
                .put("music", JSONArray(music.values.toList().take(20)))
                .put("news", JSONArray(news.values.toList().take(20)))
                .put("message", if (following && sources.isEmpty()) "Follow YouTube channels, news publishers or musicians in Search to build this feed."
                    else if (following && orderedVideos.isEmpty() && music.isEmpty() && news.isEmpty()) "No recent items were found from your followed sources."
                    else "").toString())
        }
    }

    @JavascriptInterface
    fun loadMusicSuggestions() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val follows = FeedPreferences.sources(prefs).filter { it.kind == "artist" }
            val history = runCatching { JSONArray(prefs.getString("music_history", "[]") ?: "[]") }
                .getOrDefault(JSONArray())
            val historyEntries = (0 until minOf(history.length(), 30))
                .mapNotNull { history.optJSONObject(it) }
            val playedIds = historyEntries.mapTo(mutableSetOf()) { it.optString("id") }
                .filter(String::isNotBlank).toSet()
            val artistWeights = LinkedHashMap<String, Int>()
            follows.forEach { artistWeights[it.name] = 75 }
            historyEntries.forEachIndexed { index, track ->
                val artist = track.optString("artist").trim()
                if (artist.isNotBlank()) {
                    artistWeights[artist] = (artistWeights[artist] ?: 0) + (50 - index).coerceAtLeast(5)
                }
            }

            if (artistWeights.isEmpty() && historyEntries.isEmpty()) {
                publish("rushMusicSuggestionResults", JSONObject().put("items", JSONArray())
                    .put("message", "Play a few tracks or follow musicians to build your listening profile.").toString())
                return@launch
            }

            val stopWords = setOf(
                "the", "and", "for", "with", "from", "this", "that", "your", "you", "official",
                "audio", "video", "lyrics", "lyric", "live", "music", "song", "songs", "feat",
                "ft", "remix", "version", "visualizer", "topic", "provided", "explicit"
            )
            val tokenWeights = LinkedHashMap<String, Int>()
            historyEntries.forEachIndexed { index, track ->
                val title = track.optString("title")
                val artist = track.optString("artist")
                val artistTokens = artist.lowercase(java.util.Locale.ROOT)
                    .split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).toSet()
                title.lowercase(java.util.Locale.ROOT)
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.length >= 3 && it !in stopWords && it !in artistTokens }
                    .distinct()
                    .forEach { token ->
                        tokenWeights[token] = (tokenWeights[token] ?: 0) + (30 - index).coerceAtLeast(2)
                    }
            }

            val queries = LinkedHashMap<String, Int>()
            follows.take(4).forEach { queries[it.name] = 95 }
            artistWeights.entries.sortedByDescending { it.value }.take(5).forEach { (artist, weight) ->
                queries[artist] = maxOf(queries[artist] ?: 0, weight)
            }
            historyEntries.take(3).forEachIndexed { index, track ->
                val artist = track.optString("artist").trim()
                val title = track.optString("title").trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    val seed = "$artist $title"
                    queries[seed] = maxOf(queries[seed] ?: 0, 65 - index * 8)
                }
            }
            tokenWeights.entries.sortedByDescending { it.value }.take(2).forEach { (token, weight) ->
                val query = "$token music"
                queries[query] = maxOf(queries[query] ?: 0, 20 + weight)
            }

            val suggestions = LinkedHashMap<String, Pair<Int, JSONObject>>()
            withContext(Dispatchers.IO) {
                queries.entries.take(8).forEach { (query, queryWeight) ->
                    runCatching {
                        LiveYouTubeSearch.search(query, music = true).forEach { item ->
                            if (item.videoId.isBlank() || item.videoId in playedIds) return@forEach
                            val creator = item.creator.lowercase(java.util.Locale.ROOT)
                            val candidateText = (item.title + " " + item.creator).lowercase(java.util.Locale.ROOT)
                            val artistScore = artistWeights.entries.maxOfOrNull { (artist, weight) ->
                                val key = artist.lowercase(java.util.Locale.ROOT)
                                when {
                                    creator == key -> weight * 2
                                    creator.contains(key) || key.contains(creator) -> weight
                                    else -> 0
                                }
                            } ?: 0
                            val tasteTermsScore = tokenWeights.entries
                                .filter { (token, _) -> candidateText.contains(token) }
                                .sumOf { it.value }
                            val followedArtistBoost = if (follows.any {
                                creator.contains(it.name.lowercase(java.util.Locale.ROOT))
                            }) 80 else 0
                            val score = queryWeight + artistScore + tasteTermsScore + followedArtistBoost
                            val json = JSONObject().put("id", item.videoId).put("title", item.title)
                                .put("artist", item.creator).put("duration", item.duration)
                                .put("thumbnail", item.thumbnailUrl)
                            val old = suggestions[item.videoId]
                            if (old == null || score > old.first) suggestions[item.videoId] = score to json
                        }
                    }
                }
            }

            val sorted = suggestions.values.sortedByDescending { it.first }
            val artistCounts = LinkedHashMap<String, Int>()
            val selected = ArrayList<JSONObject>()
            sorted.forEach { candidate ->
                val artist = candidate.second.optString("artist")
                if ((artistCounts[artist] ?: 0) < 4 && selected.size < 24) {
                    selected += candidate.second
                    artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                }
            }
            if (selected.size < 12) {
                sorted.forEach { candidate ->
                    if (selected.size < 24 && selected.none { it.optString("id") == candidate.second.optString("id") }) {
                        selected += candidate.second
                    }
                }
            }
            publish("rushMusicSuggestionResults", JSONObject().put("items", JSONArray(selected))
                .put("message", if (selected.isEmpty()) "No suggestions found for your listening profile yet." else "").toString())
        }
    }

    @JavascriptInterface
    fun loadPersonalizedNews() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val topics = FeedPreferences.topics(prefs)
            val followedPublishers = FeedPreferences.sources(prefs).filter { it.kind == "news" }
            val queries = (topics.take(3) + followedPublishers.take(2).map { it.name }).distinct()
            val merged = LinkedHashMap<String, Pair<Int, JSONObject>>()
            withContext(Dispatchers.IO) {
                queries.forEach { query ->
                    runCatching {
                        val items = requestNews(query)
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val publisher = item.optString("source")
                            val followBoost = if (followedPublishers.any { publisher.contains(it.name, true) || it.name.contains(publisher, true) }) 18 else 0
                            val score = FeedPreferences.rank(item.optString("title"), item.optString("summary"), topics) + followBoost
                            val key = item.optString("url")
                            val previous = merged[key]
                            if (previous == null || score > previous.first) merged[key] = score to item
                        }
                    }
                }
            }
            val articles = merged.values.sortedByDescending { it.first }.take(40).map { it.second }
            publish("rushNewsResults", JSONArray(articles).toString())
        }
    }

    @JavascriptInterface
    fun loadCurrentPlayback() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                val item = controller.currentMediaItem
                val metadata = item?.mediaMetadata
                val hasItem = item != null
                val audioOnly = metadata?.extras?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) == true
                publish("rushCurrentPlayback", JSONObject()
                    .put("hasItem", hasItem)
                    .put("isMusic", hasItem && audioOnly)
                    .put("isVideo", hasItem && !audioOnly)
                    .put("isPlaying", controller.isPlaying)
                    .put("title", metadata?.title?.toString().orEmpty())
                    .put("artist", metadata?.artist?.toString().orEmpty())
                    .put("artwork", metadata?.artworkUri?.toString().orEmpty())
                    .put("mediaId", item?.mediaId.orEmpty())
                    .put("position", controller.currentPosition)
                    .put("duration", controller.duration).toString())
            }.onFailure {
                publish("rushCurrentPlayback", JSONObject().put("hasItem", false).put("isMusic", false)
                    .put("isVideo", false).put("isPlaying", false).toString())
            }
        }
    }

    @JavascriptInterface
    fun togglePlayback() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                if (controller.isPlaying) controller.pause() else controller.play()
                loadCurrentPlayback()
            }
        }
    }

    @JavascriptInterface
    fun setVideoFullscreen(enabled: Boolean) {
        activity.setNativeVideoFullscreen(enabled)
    }

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
                captionListener?.let { controller.removeListener(it) }
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        val lines = cueGroup.cues.mapNotNull { it.text?.toString()?.takeIf { it.isNotBlank() } }
                        publish("rushVideoCaptions", JSONArray(lines).toString())
                    }
                }
                captionListener = listener
                controller.addListener(listener)
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
                prefs.edit().remove("music_last_id").apply()
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
                publish("rushVideoPlaybackState", JSONObject().put("state", "playing")
                    .put("title", title).put("creator", creator).put("captionTracks", subtitleConfigs.size).toString())
            } catch (error: Exception) {
                publish("rushVideoPlaybackState", JSONObject().put("state", "error")
                    .put("message", playbackErrorMessage(error, "Could not play this video.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun showCurrentVideo() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                val item = controller.currentMediaItem ?: return@launch
                val audioOnly = item.mediaMetadata.extras?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) == true
                if (!audioOnly) activity.runOnUiThread { activity.showNativeVideo(controller) }
            }
        }
    }

    @JavascriptInterface
    fun updateVideoPosition() {
        activity.updateNativeVideoPosition()
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
    fun loadCurrentMusicLyrics() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        val id = prefs.getString("music_last_id", null)
        val title = prefs.getString("music_last_title", null).orEmpty()
        val artist = prefs.getString("music_last_artist", null).orEmpty()
        if (!id.isNullOrBlank() && title.isNotBlank()) loadLyrics(title, artist)
    }

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
            "science" -> "science news"
            "business" -> "business news"
            "health" -> "health news"
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
                            if (thumbnail.isBlank()) {
                                val host = runCatching { android.net.Uri.parse(link).host }.getOrNull().orEmpty()
                                if (host.isNotBlank()) thumbnail = "https://www.google.com/s2/favicons?domain=$host&sz=128"
                            }
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
        controllerFuture?.let { future ->
            if (future.isDone) runCatching { future.get().let { player -> captionListener?.let(player::removeListener) } }
            MediaController.releaseFuture(future)
        }
        captionListener = null
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
