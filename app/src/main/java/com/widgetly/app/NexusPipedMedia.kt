package com.widgetly.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val PipedLime = Color(0xFFC8F23A)
private val PipedInk = Color(0xFF0E0F0C)
private val PipedPanel = Color(0xFF1A1B17)
private val PipedMuted = Color(0xFF8B8D82)
private const val PIPED_PREFS = "nexus_piped"
private const val PIPED_DEFAULT = "https://pipedapi.adminforge.de"

internal data class PipedItem(
    val id: String, val title: String, val url: String, val thumbnail: String?,
    val uploader: String, val duration: String, val views: String
)

private object NexusPipedRepository {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    private fun base(context: android.content.Context): String =
        context.getSharedPreferences(PIPED_PREFS, android.content.Context.MODE_PRIVATE)
            .getString("api", PIPED_DEFAULT)?.trim()?.trimEnd('/') ?: PIPED_DEFAULT

    fun setBase(context: android.content.Context, value: String) {
        context.getSharedPreferences(PIPED_PREFS, android.content.Context.MODE_PRIVATE).edit().putString("api", value.trim().trimEnd('/')).apply()
    }

    private fun get(context: android.content.Context, path: String): JSONObject {
        val request = Request.Builder().url(base(context) + path).header("User-Agent", "NexusAndroid/1.0").build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Video service returned HTTP ${response.code}. Try another compatible API instance.")
            return JSONObject(raw)
        }
    }

    suspend fun search(context: android.content.Context, query: String, music: Boolean): List<PipedItem> = withContext(Dispatchers.IO) {
        val filter = if (music) "music_songs" else "videos"
        val json = get(context, "/search?q=${Uri.encode(query)}&filter=$filter")
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type")
            if (type.isNotBlank() && type != "stream") return@mapNotNull null
            val url = o.optString("url")
            val id = Regex("[?&]v=([^&]+)").find(url)?.groupValues?.get(1)
                ?: url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PipedItem(id, o.optString("title"), url, o.optString("thumbnail").takeIf { it.startsWith("http") },
                o.optString("uploaderName"), o.optString("duration"), o.optString("views"))
        }
    }

    suspend fun stream(context: android.content.Context, id: String, audioOnly: Boolean): String = withContext(Dispatchers.IO) {
        val json = get(context, "/streams/${Uri.encode(id)}")
        val candidates = if (audioOnly) json.optJSONArray("audioStreams") else json.optJSONArray("videoStreams")
        if (candidates == null || candidates.length() == 0) error("No playable stream was returned for this item.")
        val list = (0 until candidates.length()).mapNotNull { i -> candidates.optJSONObject(i) }
        val selected = if (audioOnly) list.sortedByDescending { it.optInt("bitrate") }.first()
        else list.filter { it.optString("mimeType").contains("mp4") }
            .maxByOrNull { it.optString("quality").filter(Char::isDigit).toIntOrNull() ?: 0 } ?: list.first()
        selected.optString("url").takeIf { it.startsWith("http") } ?: error("The service returned an invalid stream URL.")
    }
}

@Composable
internal fun NexusMediaPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var music by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PipedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<PipedItem?>(null) }
    var audioOnly by remember { mutableStateOf(false) }
    var api by remember { mutableStateOf(context.getSharedPreferences(PIPED_PREFS, android.content.Context.MODE_PRIVATE).getString("api", PIPED_DEFAULT) ?: PIPED_DEFAULT) }
    var showSettings by remember { mutableStateOf(false) }
    val player = remember { ExoPlayer.Builder(context).build() }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L) }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, selected) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(500)
        }
    }

    fun play(item: PipedItem, audio: Boolean) {
        scope.launch {
            loading = true; error = null
            try {
                val url = NexusPipedRepository.stream(context, item.id, audio)
                player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play()
                selected = item; audioOnly = audio
            } catch (e: Exception) { error = e.localizedMessage ?: "Playback failed." }
            finally { loading = false }
        }
    }

    if (showSettings) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VIDEO ENGINE SETTINGS", color = PipedLime, fontWeight = FontWeight.Bold)
            Text("Piped-compatible API instance", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(api, { api = it }, Modifier.fillMaxWidth(), label = { Text("API base URL") }, singleLine = true)
            Button(onClick = { NexusPipedRepository.setBase(context, api); showSettings = false }, colors = ButtonDefaults.buttonColors(containerColor = PipedLime, contentColor = PipedInk)) { Text("SAVE INSTANCE") }
            Text("Nexus uses a Piped-compatible service API to search YouTube and retrieve stream URLs. Public instances can be unavailable or rate-limited; you can change the endpoint.", color = PipedMuted, fontSize = 11.sp)
            TextButton(onClick = { showSettings = false }) { Text("BACK", color = PipedLime) }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEXUS MEDIA", color = PipedLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(if (music) "Music" else "YouTube", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { showSettings = true }) { Text("ENGINE ⚙", color = PipedMuted, fontSize = 9.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(selected = !music, onClick = { music = false; selected = null }, label = { Text("YOUTUBE") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PipedLime, selectedLabelColor = PipedInk, labelColor = Color.White))
            FilterChip(selected = music, onClick = { music = true; selected = null }, label = { Text("MUSIC") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PipedLime, selectedLabelColor = PipedInk, labelColor = Color.White))
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (music) "Search songs, artists or albums" else "Search YouTube") },
            trailingIcon = { TextButton(onClick = {
                if (query.isNotBlank()) scope.launch {
                    loading = true; error = null
                    try { results = NexusPipedRepository.search(context, query.trim(), music) }
                    catch (e: Exception) { error = e.localizedMessage ?: "Search failed." }
                    finally { loading = false }
                }
            }) { Text("SEARCH", color = PipedLime) } })
        if (selected != null) {
            Column(Modifier.fillMaxWidth().background(PipedPanel, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (audioOnly) "NOW PLAYING · AUDIO" else "NOW PLAYING · VIDEO", color = PipedLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(selected!!.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (!audioOnly) AndroidView(Modifier.fillMaxWidth().aspectRatio(16f / 9f), factory = { ctx -> PlayerView(ctx).apply { player = this@NexusMediaPagePlayerPlaceholder } })
                Slider(value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f,
                    onValueChange = { position = it.toLong() },
                    onValueChangeFinished = { if (duration > 0) player.seekTo(position.coerceIn(0, duration)) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(), enabled = duration > 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMediaTime(position), color = PipedMuted, fontSize = 9.sp)
                    TextButton(onClick = { if (playing) player.pause() else player.play() }) { Text(if (playing) "PAUSE" else "PLAY", color = PipedLime) }
                    Text(formatMediaTime(duration), color = PipedMuted, fontSize = 9.sp)
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PipedLime)
        error?.let { Text(it, color = Color(0xFFFF9A80), fontSize = 11.sp) }
        if (results.isEmpty() && !loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Search for a video or song. Playback stays inside Nexus.", color = PipedMuted, fontSize = 12.sp)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(results, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().background(PipedPanel, RoundedCornerShape(10.dp)).clickable { play(item, music) }.padding(9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.thumbnail != null) coil.compose.AsyncImage(item.thumbnail, contentDescription = item.title, modifier = Modifier.width(112.dp).height(68.dp))
                    else Box(Modifier.width(112.dp).height(68.dp).background(Color(0xFF30352A)), contentAlignment = Alignment.Center) { Text("▶", color = PipedLime) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(item.uploader, color = PipedMuted, fontSize = 9.sp, maxLines = 1)
                        Text("${item.duration}  ·  ${item.views}", color = PipedMuted, fontSize = 9.sp)
                        Text(if (music) "PLAY AUDIO INSIDE NEXUS" else "PLAY VIDEO INSIDE NEXUS", color = PipedLime, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** In-app player for a video opened from the finite Discover feed. */
@Composable
internal fun NexusPipedInlinePlayer(url: String, title: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var streamUrl by remember(url) { mutableStateOf<String?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val id = Regex("[?&]v=([^&]+)").find(url)?.groupValues?.get(1)
        ?: Regex("youtu\\.be/([^?&/]+)").find(url)?.groupValues?.get(1)
    val player = remember(url) { ExoPlayer.Builder(context).build() }
    LaunchedEffect(url) {
        if (id == null) { error = "Could not identify this YouTube video." }
        else try {
            val stream = NexusPipedRepository.stream(context, id, false)
            streamUrl = stream; player.setMediaItem(MediaItem.fromUri(stream)); player.prepare(); player.play()
        } catch (e: Exception) { error = e.localizedMessage ?: "Could not load video." }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        AndroidView(Modifier.fillMaxWidth().aspectRatio(16f / 9f), factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, update = { it.player = player })
        if (streamUrl == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PipedLime)
        error?.let { Text(it, color = Color(0xFFFF9A80), fontSize = 11.sp) }
    }
}
