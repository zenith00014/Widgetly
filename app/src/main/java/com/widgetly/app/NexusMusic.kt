package com.widgetly.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private data class LocalTrack(val uri: String, val title: String)

private object NexusMusicStore {
    private const val KEY = "nexus_local_music"
    fun load(context: Context): List<LocalTrack> = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri")
            if (uri.isBlank()) null else LocalTrack(uri, o.optString("title", "Audio track"))
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, tracks: List<LocalTrack>) {
        val array = JSONArray()
        tracks.distinctBy { it.uri }.take(300).forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

private fun trackTitle(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.', uri.lastPathSegment ?: "Audio track") ?: (uri.lastPathSegment ?: "Audio track")
}

private fun timeLabel(ms: Long): String {
    if (ms < 0) return "0:00"
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
fun NexusMusicPage(player: ExoPlayer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tracks by remember { mutableStateOf(NexusMusicStore.load(context)) }
    var currentUri by remember { mutableStateOf(player.currentMediaItem?.localConfiguration?.uri?.toString()) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val additions = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                LocalTrack(uri.toString(), trackTitle(context, uri))
            }.getOrNull()
        }
        if (additions.isNotEmpty()) {
            tracks = (tracks + additions).distinctBy { it.uri }.take(300)
            NexusMusicStore.save(context, tracks)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentUri = mediaItem?.localConfiguration?.uri?.toString()
                duration = player.duration.coerceAtLeast(0L)
                position = 0L
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_IDLE) error = "This audio file could not be opened."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(500)
        }
    }

    fun play(index: Int) {
        if (index !in tracks.indices) return
        error = null
        val items = tracks.map { MediaItem.fromUri(Uri.parse(it.uri)) }
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.play()
        currentUri = tracks[index].uri
    }

    val current = tracks.firstOrNull { it.uri == currentUri }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("YOUR MUSIC", color = Color(0xFFC8F23A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Files you choose stay on this device.", color = Color(0xFF8B8D82), fontSize = 11.sp)
            }
            Button(onClick = { picker.launch(arrayOf("audio/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8F23A), contentColor = Color(0xFF0E0F0C))) {
                Text("＋ ADD MUSIC", fontSize = 10.sp)
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(current?.title ?: "NOTHING PLAYING", color = Color(0xFFECE9DF), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Slider(
                value = if (duration > 0) position.coerceIn(0L, duration).toFloat() else 0f,
                onValueChange = { position = it.toLong() },
                onValueChangeFinished = { if (duration > 0) player.seekTo(position.coerceIn(0L, duration)) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = current != null && duration > 0
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel(position), color = Color(0xFF8B8D82), fontSize = 10.sp)
                Text(timeLabel(duration), color = Color(0xFF8B8D82), fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }) { Text("◀|", color = Color(0xFFECE9DF), fontSize = 18.sp) }
                Button(onClick = { if (player.isPlaying) player.pause() else if (player.currentMediaItem != null) player.play() else if (tracks.isNotEmpty()) play(0) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8F23A), contentColor = Color(0xFF0E0F0C))) {
                    Text(if (playing) "Ⅱ" else "▶", fontSize = 18.sp)
                }
                TextButton(onClick = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }) { Text("|▶", color = Color(0xFFECE9DF), fontSize = 18.sp) }
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFFF9A80), fontSize = 11.sp)
        Text("${tracks.size} TRACKS · LOCAL FILES", color = Color(0xFF8B8D82), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (tracks.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YOUR PERSONAL MUSIC SHELF", color = Color(0xFFC8F23A), fontWeight = FontWeight.Bold)
                Text("Choose audio files from your phone. Nexus does not scan your storage or upload your music.", color = Color(0xFF8B8D82), fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.uri }) { track ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).clickable { play(tracks.indexOf(track)) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = if (track.uri == currentUri) Color(0xFFC8F23A) else Color(0xFFECE9DF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(if (track.uri == currentUri && playing) "NOW PLAYING" else "LOCAL AUDIO", color = Color(0xFF8B8D82), fontSize = 9.sp)
                        }
                        TextButton(onClick = {
                            if (track.uri == currentUri) player.stop()
                            tracks = tracks.filterNot { it.uri == track.uri }
                            NexusMusicStore.save(context, tracks)
                            if (track.uri == currentUri) currentUri = null
                        }) { Text("REMOVE", color = Color(0xFF8B8D82), fontSize = 9.sp) }
                    }
                }
            }
        }
    }
}
