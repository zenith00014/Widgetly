package com.widgetly.app

import android.content.Context
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
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Locale

private const val NEXUS_FTP_HOST = "192.168.100.100"
private const val NEXUS_FTP_PORT = 21
private const val NEXUS_FTP_CACHE = "nexus_ftp_movie_catalogue"
private const val NEXUS_FTP_CACHE_KEY = "movies_v1"
private val FtpLime = Color(0xFFC8F23A)
private val FtpInk = Color(0xFF0E0F0C)
private val FtpPanel = Color(0xFF1A1B17)
private val FtpMuted = Color(0xFF8B8D82)

internal data class NexusFtpMovie(val name: String, val size: Long, val modifiedMillis: Long, val uri: String) {
    val displayName: String get() = name.substringBeforeLast('.', name)
    val sizeLabel: String get() = if (size <= 0L) "Unknown size" else String.format(Locale.getDefault(), "%.2f GB", size / 1_073_741_824.0)
}

internal object NexusFtpRepository {
    private val extensions = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp", "ts", "mpeg", "mpg")

    fun cached(context: Context): List<NexusFtpMovie> {
        val raw = context.getSharedPreferences(NEXUS_FTP_CACHE, Context.MODE_PRIVATE).getString(NEXUS_FTP_CACHE_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                NexusFtpMovie(o.getString("name"), o.optLong("size"), o.optLong("modified"), o.getString("uri"))
            }
        }.getOrDefault(emptyList())
    }

    fun refresh(context: Context): List<NexusFtpMovie> {
        val ftp = FTPClient()
        ftp.connectTimeout = 10_000
        ftp.defaultTimeout = 10_000
        ftp.setDataTimeout(30_000)
        try {
            ftp.connect(NEXUS_FTP_HOST, NEXUS_FTP_PORT)
            if (!ftp.login("anonymous", "nexus@android.local")) throw IOException("Anonymous FTP login was rejected (${ftp.replyCode}).")
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            val files = ftp.listFiles("/")
            if (ftp.replyCode !in 200..299) throw IOException("Could not read the FTP root directory (${ftp.replyCode}).")
            val movies = files.asSequence().filter { it.isFile && it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions }
                .map { file ->
                    val uri = Uri.Builder().scheme("ftp").encodedAuthority("$NEXUS_FTP_HOST:$NEXUS_FTP_PORT").appendPath(file.name).build().toString()
                    NexusFtpMovie(file.name, file.size, file.timestamp?.timeInMillis ?: 0L, uri)
                }.sortedBy { it.displayName.lowercase(Locale.ROOT) }.toList()
            val array = JSONArray()
            movies.forEach { array.put(JSONObject().put("name", it.name).put("size", it.size).put("modified", it.modifiedMillis).put("uri", it.uri)) }
            context.getSharedPreferences(NEXUS_FTP_CACHE, Context.MODE_PRIVATE).edit().putString(NEXUS_FTP_CACHE_KEY, array.toString()).apply()
            return movies
        } finally {
            runCatching { if (ftp.isConnected) ftp.logout() }
            runCatching { if (ftp.isConnected) ftp.disconnect() }
        }
    }
}

/** Media3 FTP source. Seeking uses FTP REST and depends on server support. */
internal class NexusFtpDataSource : BaseDataSource(true) {
    private var client: FTPClient? = null
    private var stream: InputStream? = null
    private var openedUri: Uri? = null
    private var remaining = C.LENGTH_UNSET
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val parsed = runCatching { URI(dataSpec.uri.toString()) }.getOrElse { throw IOException("Invalid FTP media address.", it) }
        val host = parsed.host ?: throw IOException("FTP address has no host.")
        val port = if (parsed.port > 0) parsed.port else NEXUS_FTP_PORT
        val path = parsed.path?.takeIf { it.isNotBlank() } ?: throw IOException("FTP address has no file path.")
        val ftp = FTPClient()
        try {
            ftp.connectTimeout = 10_000
            ftp.defaultTimeout = 10_000
            ftp.setDataTimeout(30_000)
            ftp.connect(host, port)
            if (!ftp.login("anonymous", "nexus@android.local")) throw IOException("Anonymous FTP login failed.")
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)
            val metadata = runCatching { ftp.mlistFile(path) }.getOrNull()
            if (dataSpec.position > 0L) ftp.setRestartOffset(dataSpec.position)
            val input = ftp.retrieveFileStream(path) ?: throw IOException("FTP server refused this file (${ftp.replyCode}).")
            client = ftp
            stream = input
            openedUri = dataSpec.uri
            remaining = if (dataSpec.length != C.LENGTH_UNSET) dataSpec.length
                else if (metadata != null && metadata.size >= dataSpec.position) metadata.size - dataSpec.position
                else C.LENGTH_UNSET
            opened = true
            transferStarted(dataSpec)
            return remaining
        } catch (e: Exception) {
            runCatching { if (ftp.isConnected) ftp.disconnect() }
            if (e is IOException) throw e
            throw IOException("Unable to open provider FTP stream.", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val allowed = if (remaining == C.LENGTH_UNSET) length else minOf(length.toLong(), remaining).toInt()
        val count = stream?.read(buffer, offset, allowed) ?: throw IOException("FTP stream is not open.")
        if (count == -1) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET) remaining -= count.toLong()
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        val wasOpened = opened
        opened = false
        runCatching { stream?.close() }
        stream = null
        val ftp = client
        client = null
        if (ftp != null) {
            runCatching { ftp.completePendingCommand() }
            runCatching { if (ftp.isConnected) ftp.logout() }
            runCatching { if (ftp.isConnected) ftp.disconnect() }
        }
        openedUri = null
        remaining = C.LENGTH_UNSET
        if (wasOpened) transferEnded()
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = NexusFtpDataSource()
    }
}

@Composable
internal fun NexusMoviesPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var movies by remember { mutableStateOf(NexusFtpRepository.cached(context)) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<NexusFtpMovie?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try { movies = withContext(Dispatchers.IO) { NexusFtpRepository.refresh(context) } }
            catch (e: Exception) { error = e.localizedMessage ?: "FTP server unavailable." }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { if (movies.isEmpty()) refresh() }

    if (selected != null) {
        NexusFtpMoviePlayer(selected!!, { selected = null }, modifier)
        return
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PROVIDER LIBRARY", color = FtpLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("${movies.size} movies indexed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { refresh() }, enabled = !loading) { Text(if (loading) "SCANNING…" else "↻ REFRESH", color = FtpLime, fontSize = 10.sp) }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Search provider movies") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FtpLime, focusedLabelColor = FtpLime))
        Spacer(Modifier.height(8.dp))
        Text("FTP  ·  $NEXUS_FTP_HOST  ·  ANONYMOUS", color = FtpMuted, fontSize = 9.sp)
        if (loading && movies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FtpLime) }
        } else if (error != null && movies.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SERVER UNAVAILABLE", color = FtpLime, fontWeight = FontWeight.Bold)
                Text(error!!, color = FtpMuted, fontSize = 12.sp)
                Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = FtpLime, contentColor = FtpInk)) { Text("RETRY") }
                Text("Connect to your provider Wi-Fi and try again.", color = FtpMuted, fontSize = 10.sp)
            }
        } else {
            if (error != null) Text("Showing cached catalogue. Refresh failed: $error", color = FtpMuted, fontSize = 10.sp)
            val filtered = movies.filter { it.displayName.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (movies.isEmpty()) "No video files found in the FTP root." else "No matching movies.", color = FtpMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)) {
                    items(filtered, key = { it.uri }) { movie ->
                        Row(Modifier.fillMaxWidth().background(FtpPanel, RoundedCornerShape(12.dp)).clickable { selected = movie }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(58.dp, 72.dp).background(Color(0xFF30352A), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Text("▶", color = FtpLime, fontSize = 24.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(movie.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(movie.sizeLabel, color = FtpMuted, fontSize = 10.sp)
                                Text("PLAY INSIDE NEXUS  ↗", color = FtpLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NexusFtpMoviePlayer(movie: NexusFtpMovie, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(movie.uri) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(NexusFtpDataSource.Factory()))
            .build().apply { setMediaItem(MediaItem.fromUri(movie.uri)); prepare(); playWhenReady = true }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    Column(modifier.fillMaxSize()) {
        Text("← BACK TO MOVIES", color = FtpLime, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            modifier = Modifier.clickable(onClick = onBack).padding(12.dp))
        AndroidView(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
            update = { it.player = exoPlayer })
        Text(movie.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(14.dp))
        Text("Provider FTP stream · seeking depends on FTP REST support", color = FtpMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}
