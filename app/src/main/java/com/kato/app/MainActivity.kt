package com.kato.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NexusInk = Color(0xFF0E0F0C)
private val NexusPanel = Color(0xFF1A1B17)
private val NexusRaised = Color(0xFF24251F)
private val NexusLine = Color(0xFF3A3C34)
private val NexusLime = Color(0xFFC8F23A)
private val NexusAmber = Color(0xFFFFB020)
private val NexusPaper = Color(0xFFECE9DF)
private val NexusMuted = Color(0xFF8B8D82)

private val NexusDarkColors = darkColorScheme(
    primary = NexusLime,
    onPrimary = NexusInk,
    secondary = NexusAmber,
    onSecondary = NexusInk,
    background = NexusInk,
    onBackground = NexusPaper,
    surface = NexusPanel,
    onSurface = NexusPaper,
    surfaceVariant = NexusRaised,
    onSurfaceVariant = NexusMuted,
    outline = NexusLine,
    tertiary = NexusAmber
)

private val NexusLightColors = lightColorScheme(
    primary = Color(0xFF596B3D),
    onPrimary = Color.White,
    secondary = Color(0xFF87602C),
    onSecondary = Color.White,
    background = Color(0xFFECE9DF),
    onBackground = Color(0xFF11120F),
    surface = Color(0xFFF8F6EE),
    onSurface = Color(0xFF11120F),
    surfaceVariant = Color(0xFFE2DFD4),
    onSurfaceVariant = Color(0xFF5C5E54),
    outline = Color(0xFF11120F),
    tertiary = Color(0xFF8A5A00)
)

private val topicOptions = listOf(
    "World", "Bangladesh", "Technology", "Science", "Business", "Football", "Education"
)

private data class InboxEntry(
    val id: String,
    val source: String,
    val title: String,
    val message: String,
    val time: Long,
    val unread: Boolean,
    val avatar: String,
    val packageName: String
)

private data class SocialEntry(
    val id: String,
    val type: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val image: String,
    val publishedAt: Long,
    val videoId: String = "",
    val score: Float = 0f,
    val topic: String = "General",
    val why: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusApp() }
    }
}

@Composable
private fun NexusApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE) }
    var page by rememberSaveable { mutableStateOf("Home") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "Dark") ?: "Dark") }
    var topics by remember {
        mutableStateOf(
            prefs.getString("selected_topics", "World,Bangladesh,Technology,Science,Education,Football")
                .orEmpty().split(",").filter { it in topicOptions }.toSet()
        )
    }
    var channelText by remember { mutableStateOf(prefs.getString("youtube_channels", "") ?: "") }
    var customFeedText by remember { mutableStateOf(prefs.getString("custom_news_feeds", "") ?: "") }
    var spotifyUrl by remember { mutableStateOf(prefs.getString("spotify_url", "") ?: "") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var news by remember { mutableStateOf<List<NexusNews>>(emptyList()) }
    var videos by remember { mutableStateOf<List<NexusVideo>>(emptyList()) }
    var movies by remember { mutableStateOf<List<NexusMovie>>(emptyList()) }
    var playerType by rememberSaveable { mutableStateOf("") }
    var playerTitle by rememberSaveable { mutableStateOf("") }
    var playerTarget by rememberSaveable { mutableStateOf("") }
    var playerVideoId by rememberSaveable { mutableStateOf("") }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "Light" -> false
        "System" -> systemDark
        else -> true
    }

    LaunchedEffect(refreshKey, topics, channelText, customFeedText) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                NexusContentRepository.fetchNews(topics, customFeedText),
                NexusContentRepository.fetchVideos(channelText),
                NexusContentRepository.fetchPublicDomainMovies()
            )
        }
        news = loaded.first
        videos = loaded.second
        movies = loaded.third
        isLoading = false
    }

    fun openVideo(video: NexusVideo) {
        playerType = "YouTube"
        playerTitle = video.title
        playerTarget = video.url
        playerVideoId = video.videoId
    }

    fun openSpotify() {
        val embed = spotifyEmbedUrl(spotifyUrl)
        if (embed.isNotBlank()) {
            playerType = "Spotify"
            playerTitle = "Spotify"
            playerTarget = embed
            playerVideoId = ""
        }
    }

    fun closePlayer() {
        playerType = ""
        playerTitle = ""
        playerTarget = ""
        playerVideoId = ""
    }

    BackHandler(enabled = playerType.isNotBlank() || showSettings) {
        if (playerType.isNotBlank()) closePlayer() else showSettings = false
    }

    MaterialTheme(colorScheme = if (useDark) NexusDarkColors else NexusLightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    page = if (playerType.isNotBlank()) playerTitle else if (showSettings) "Settings" else page,
                    onRefresh = { refreshKey++ },
                    onSettings = { showSettings = true },
                    showRefresh = playerType.isBlank() && !showSettings && page != "Library"
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        playerType.isNotBlank() -> MediaPlayerScreen(
                            type = playerType,
                            title = playerTitle,
                            target = playerTarget,
                            videoId = playerVideoId,
                            onBack = ::closePlayer
                        )
                        showSettings -> NexusSettingsScreen(
                            themeMode = themeMode,
                            topics = topics,
                            channelText = channelText,
                            customFeedText = customFeedText,
                            spotifyUrl = spotifyUrl,
                            onBack = { showSettings = false },
                            onThemeChange = {
                                themeMode = it
                                prefs.edit().putString("theme_mode", it).apply()
                            },
                            onTopicsChange = {
                                topics = it
                                prefs.edit().putString("selected_topics", it.joinToString(",")).apply()
                            },
                            onChannelsChange = {
                                channelText = it
                                prefs.edit().putString("youtube_channels", it).apply()
                            },
                            onCustomFeedsChange = {
                                customFeedText = it
                                prefs.edit().putString("custom_news_feeds", it).apply()
                            },
                            onSpotifyChange = {
                                spotifyUrl = it
                                prefs.edit().putString("spotify_url", it).apply()
                            },
                            onRefresh = { refreshKey++ }
                        )
                        page == "Home" -> HomeScreen(
                            refreshKey = refreshKey,
                            news = news,
                            videos = videos,
                            movies = movies,
                            loading = isLoading,
                            spotifyUrl = spotifyUrl,
                            onOpenInbox = { page = "Inbox" },
                            onOpenNews = { page = "Social" },
                            onOpenWeb = { url, title ->
                                playerType = "Web"
                                playerTitle = title
                                playerTarget = url
                                playerVideoId = ""
                            },
                            onOpenVideo = ::openVideo,
                            onOpenSpotify = ::openSpotify,
                            onOpenMovie = { movie ->
                                playerType = "Movie"
                                playerTitle = movie.title
                                playerTarget = movie.url
                                playerVideoId = ""
                            },
                            onOpenLibrary = { page = "Library" }
                        )
                        page == "Social" -> SocialScreen(
                            topics = topics,
                            news = news,
                            videos = videos,
                            loading = isLoading,
                            onOpenVideo = ::openVideo,
                            onOpenArticle = { url, title ->
                                playerType = "Web"
                                playerTitle = title
                                playerTarget = url
                                playerVideoId = ""
                            },
                            onRefresh = { refreshKey++ },
                            onSave = { title, url, type ->
                                KatoDatabase(context).saveItem(title, url, type)
                            },
                            onLike = { title, url, type ->
                                KatoDatabase(context).saveItem(title, url, type, liked = true)
                            }
                        )
                        page == "Inbox" -> InboxScreen(
                            refreshKey = refreshKey,
                            onRefresh = { refreshKey++ }
                        )
                        else -> LibraryScreen(
                            onOpenSettings = { showSettings = true },
                            onOpenUrl = { url, title ->
                                playerType = "Web"
                                playerTitle = title
                                playerTarget = url
                                playerVideoId = ""
                            }
                        )
                    }
                }

                if (playerType.isBlank() && !showSettings) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        val tabs = listOf(
                            Triple("Home", "⌂", "HOME"),
                            Triple("Social", "◉", "SOCIAL"),
                            Triple("Inbox", "▤", "INBOX"),
                            Triple("Library", "▣", "LIBRARY")
                        )
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = page == tab.first,
                                onClick = { page = tab.first },
                                icon = { Text(tab.second, fontSize = 19.sp, fontWeight = FontWeight.Medium) },
                                label = { Text(tab.third, fontSize = 9.sp, letterSpacing = 0.5.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    page: String,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    showRefresh: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 19.dp, end = 14.dp, top = 13.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("NEXUS", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(
                when (page) {
                    "Home" -> "Your world, without the scroll"
                    "Social" -> "A feed chosen by you"
                    "Inbox" -> "People, not platforms"
                    "Library" -> "Your saved corner"
                    "Settings" -> "Preferences and sources"
                    else -> page
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showRefresh) {
            TextButton(onClick = onRefresh) { Text("↻", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        TextButton(onClick = onSettings) { Text("⚙", fontSize = 18.sp) }
    }
}

@Composable
private fun HomeScreen(
    refreshKey: Int,
    news: List<NexusNews>,
    videos: List<NexusVideo>,
    movies: List<NexusMovie>,
    loading: Boolean,
    spotifyUrl: String,
    onOpenInbox: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenVideo: (NexusVideo) -> Unit,
    onOpenSpotify: () -> Unit,
    onOpenMovie: (NexusMovie) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = LocalContext.current
    val inbox = remember(refreshKey) { readInbox(context) }
    val unread = inbox.filter { it.unread }
    val selectedNews = news.sortedByDescending { it.publishedAt }.take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 4.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Card(
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("A LITTLE LESS NOISE.", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Stay connected.\nStay curious.", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                        Text("No ads. No subscriptions. Your topics.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier.size(68.dp).clip(RectangleShape).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("N", color = MaterialTheme.colorScheme.primary, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item { SectionHeading("INBOX", "People who have messaged you") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenInbox),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (unread.isEmpty()) "All caught up" else "${unread.size} unread conversations", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("WhatsApp · Telegram · Instagram · Messenger", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        Text("OPEN →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                    if (unread.isEmpty()) {
                        Text("Your recent message previews will appear here after notification access is enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(unread.take(8), key = { it.id }) { entry ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    NexusAvatar(entry, Modifier.size(48.dp))
                                    Text(entry.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.source, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeading("WORLD BRIEF", "The latest from your selected topics") }
        if (selectedNews.isEmpty()) {
            item {
                QuietCard(
                    title = if (loading) "Gathering the latest stories…" else "No stories loaded yet",
                    detail = "News comes from free RSS feeds. Open Settings to choose topics or add another RSS source."
                )
            }
        } else {
            items(selectedNews.take(5), key = { it.url }) { story ->
                NewsCard(story, compact = true, onClick = { onOpenWeb(story.url, story.title) })
            }
        }
        item {
            TextButton(onClick = onOpenNews) { Text("OPEN SOCIAL FEED  →") }
        }

        item { SectionHeading("YOUTUBE", "Selected channels, newest uploads") }
        if (videos.isEmpty()) {
            item {
                QuietCard(
                    title = "Follow your own channels",
                    detail = "Add YouTube channel IDs in Library → Settings. Nexus reads each channel's upload feed without a paid API key."
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(videos.take(3), key = { it.videoId }) { video ->
                        VideoTile(video, Modifier.width(230.dp), onClick = { onOpenVideo(video) })
                    }
                }
            }
        }

        item { SectionHeading("SPOTIFY", "Your playlist or album") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = spotifyUrl.isNotBlank(), onClick = onOpenSpotify),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(49.dp).clip(RectangleShape).background(Color(0xFF1D3A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♫", color = Color(0xFFB9F28B), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (spotifyUrl.isBlank()) "Connect a playlist" else "Your Spotify collection", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (spotifyUrl.isBlank()) "Add a Spotify playlist or album link in Library → Settings."
                            else "Open the official Spotify embed inside Nexus.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Text(if (spotifyUrl.isBlank()) "+" else "PLAY", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Spotify controls and playback are supplied by Spotify. Nexus does not add ads, but cannot remove ads or account limits served by Spotify.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 13.sp)
        }

        item { SectionHeading("MOVIE LOUNGE", "Free-to-watch public-domain discoveries") }
        if (movies.isEmpty()) {
            item {
                QuietCard(
                    title = if (loading) "Checking the public-domain catalog…" else "No verified titles available right now",
                    detail = "Nexus checks Internet Archive license metadata. A title is shown only when the catalog reports a public-domain license."
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(movies.take(3), key = { it.identifier }) { movie ->
                        MovieTile(movie, Modifier.width(160.dp), onClick = { onOpenMovie(movie) })
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenLibrary) { Text("OPEN YOUR LIBRARY  →") }
                Spacer(Modifier.weight(1f))
                Text("FINITE BY DESIGN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }
    }
}

private fun buildNexusSocialSession(
    news: List<NexusNews>,
    videos: List<NexusVideo>,
    selectedTopics: Set<String>,
    savedTitles: List<String>,
    sessionSize: Int
): List<SocialEntry> {
    val keywords = mapOf(
        "World" to listOf("world", "global", "international", "united nations", "diplomacy", "climate"),
        "Bangladesh" to listOf("bangladesh", "dhaka", "bangladeshi", "south asia", "chattogram", "sylhet"),
        "Technology" to listOf("technology", "tech", "artificial intelligence", "software", "cyber", "robot", "internet"),
        "Science" to listOf("science", "research", "space", "physics", "biology", "discovery", "astronomy"),
        "Business" to listOf("business", "economy", "market", "trade", "company", "finance", "jobs"),
        "Football" to listOf("football", "soccer", "premier league", "champions league", "fifa", "goal", "club"),
        "Education" to listOf("education", "school", "university", "student", "learning")
    )
    fun tokens(text: String) = Regex("[a-z0-9]{4,}").findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.toSet()
    val savedWords = savedTitles.flatMap { tokens(it) }.toSet()
    fun topicFor(text: String): String = selectedTopics.maxByOrNull { topic ->
        keywords[topic].orEmpty().count { text.contains(it, true) }
    } ?: "General"
    fun savedSimilarity(title: String): Float {
        val words = tokens(title)
        if (words.isEmpty() || savedWords.isEmpty()) return 0f
        return (words.intersect(savedWords).size.toFloat() / words.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val pool = buildList {
        news.forEach { item ->
            val topic = if (item.topic in selectedTopics) item.topic else topicFor(item.title + " " + item.summary)
            val ageHours = if (item.publishedAt > 0L) ((System.currentTimeMillis() - item.publishedAt).coerceAtLeast(0L) / 3_600_000L).toFloat() else 168f
            val freshness = (1f - ageHours / (24f * 7f)).coerceIn(0f, 1f)
            val similar = savedSimilarity(item.title)
            val topicMatch = if (topic in selectedTopics) 1f else 0f
            val score = 0.35f * topicMatch + 0.15f * similar + 0.15f * freshness
            val why = buildList {
                if (topicMatch > 0f) add("topic $topic")
                if (similar > 0.1f) add("similar to saved")
                if (freshness > 0.6f) add("recent")
            }.joinToString(" · ")
            add(SocialEntry("news:${item.url}", "News", item.title, item.summary, item.source, item.url,
                item.imageUrl, item.publishedAt, score = score, topic = topic, why = "WHY: ${why.ifBlank { "selected source" }}"))
        }
        videos.forEach { item ->
            val text = (item.title + " " + item.channel).lowercase(Locale.ROOT)
            val topic = topicFor(text)
            val ageHours = if (item.publishedAt > 0L) ((System.currentTimeMillis() - item.publishedAt).coerceAtLeast(0L) / 3_600_000L).toFloat() else 168f
            val freshness = (1f - ageHours / (24f * 7f)).coerceIn(0f, 1f)
            val similar = savedSimilarity(item.title)
            val topicMatch = if (topic in selectedTopics && keywords[topic].orEmpty().any { text.contains(it) }) 1f else 0f
            val followed = 1f
            val score = 0.35f * topicMatch + 0.20f * followed + 0.15f * similar + 0.15f * freshness
            val why = buildList {
                if (topicMatch > 0f) add("topic $topic")
                add("followed source")
                if (similar > 0.1f) add("similar to saved")
                if (freshness > 0.6f) add("recent")
            }.joinToString(" · ")
            add(SocialEntry("video:${item.videoId}", "Video", item.title, item.channel, item.channel, item.url,
                item.thumbnail, item.publishedAt, item.videoId, score, topic, "WHY: $why"))
        }
    }.sortedByDescending { it.score }

    val remaining = pool.toMutableList()
    val result = mutableListOf<SocialEntry>()
    while (remaining.isNotEmpty() && result.size < sessionSize) {
        val next = remaining.maxByOrNull { candidate ->
            val recent = result.takeLast(2)
            val diversityBonus = if (recent.none { it.source == candidate.source || it.topic == candidate.topic }) 0.15f else 0f
            candidate.score + diversityBonus
        } ?: break
        result += next
        remaining.remove(next)
    }
    return result
}

@Composable
private fun SocialScreen(
    topics: Set<String>,
    news: List<NexusNews>,
    videos: List<NexusVideo>,
    loading: Boolean,
    onOpenVideo: (NexusVideo) -> Unit,
    onOpenArticle: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onLike: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val savedTitles = remember { KatoDatabase(context).listItems().map { it.title } }
    val entries = remember(news, videos, topics, savedTitles) {
        buildNexusSocialSession(news, videos, topics, savedTitles, sessionSize = 20)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YOUR INTERESTS", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Useful things, not endless things.", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("A finite mix of selected-topic news and uploads from channels you follow.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        if (entries.isEmpty()) {
            item {
                QuietCard(
                    title = if (loading) "Building your selection…" else "Your feed is ready for your sources",
                    detail = "Choose topics, add YouTube channel IDs or add RSS feeds in Library → Settings. Nothing is filled with fake sample posts."
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                SocialPostCard(
                    entry = entry,
                    onOpen = {
                        if (entry.videoId.isNotBlank()) {
                            onOpenVideo(NexusVideo(entry.title, entry.url, entry.videoId, entry.source, entry.publishedAt, entry.image))
                        } else {
                            onOpenArticle(entry.url, entry.title)
                        }
                    },
                    onSave = { onSave(entry.title, entry.url, if (entry.type == "Video") "Video" else "Article") },
                    onLike = { onLike(entry.title, entry.url, if (entry.type == "Video") "Video" else "Article") }
                )
            }
            item {
                Card(
                    shape = RectangleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("+---------------- SESSION END ----------------+", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        Text("You've reached the end.", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Reviewed ${entries.size} of 20 selected items. Nothing else loads until you refresh.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text("[x] SESSION COMPLETE", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("REFRESH SELECTED SOURCES") }
        }
    }
}

@Composable
private fun SocialPostCard(
    entry: SocialEntry,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onLike: () -> Unit
) {
    Card(shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            if (entry.image.isNotBlank()) {
                NexusRemoteImage(entry.image, Modifier.fillMaxWidth().height(174.dp))
            }
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.type.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(entry.source, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text(dateLabel(entry.publishedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }
                Text(entry.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                if (entry.summary.isNotBlank()) {
                    Text(entry.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (entry.why.isNotBlank()) {
                    Text(entry.why, color = MaterialTheme.colorScheme.tertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSave) { Text("＋ WATCH LATER", fontSize = 9.sp) }
                    TextButton(onClick = onLike) { Text("♡ LIKE", fontSize = 9.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpen) { Text(if (entry.type == "Video") "WATCH →" else "READ →", fontSize = 9.sp) }
                }
            }
        }
    }
}

@Composable
private fun InboxScreen(refreshKey: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val enabled = remember(refreshKey, localRefresh) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val entries = remember(refreshKey, localRefresh) { readInbox(context).sortedByDescending { it.time } }
    val visibleEntries = entries.filter { entry ->
        val matchesSearch = search.isBlank() ||
            entry.title.contains(search, true) || entry.message.contains(search, true) || entry.source.contains(search, true)
        val matchesFilter = when (filter) {
            "Unread" -> entry.unread
            "WhatsApp", "Telegram", "Instagram", "Messenger" -> entry.source == filter
            else -> true
        }
        matchesSearch && matchesFilter
    }
    val unread = entries.count { it.unread }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("MESSAGE CENTER", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text(if (unread == 0) "You're all caught up." else "$unread unread conversations", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Private message previews gathered from notifications you choose to share with Nexus.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        item {
            Card(shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification access", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(if (enabled) "Enabled" else "Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        Text(if (enabled) "ON" else "OFF", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (enabled) "MANAGE ACCESS" else "CONNECT MESSAGING APPS") }
                    Text("This is a notification-preview inbox, not a private messaging API. Full chat history and reliable in-app replies are not available through this method.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
                    TextButton(onClick = { localRefresh++; onRefresh() }) { Text("REFRESH INBOX") }
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search message previews") },
                placeholder = { Text("Name, message or app") }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("All", "Unread", "WhatsApp", "Telegram", "Instagram", "Messenger").forEach { choice ->
                    FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice, fontSize = 10.sp) })
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                QuietCard(
                    title = "No message previews yet",
                    detail = if (enabled) "When a supported app posts a message notification, the conversation will appear here."
                    else "Enable notification access, then receive a message from a supported messaging app."
                )
            }
        } else if (visibleEntries.isEmpty()) {
            item { QuietCard("No matching previews", "Change the search or filter to see other conversations.") }
        } else {
            items(visibleEntries, key = { it.id }) { entry ->
                InboxConversationCard(
                    entry = entry,
                    onRead = {
                        setInboxRead(context, entry.id)
                        localRefresh++
                        onRefresh()
                    },
                    onOpen = { launchMessagingApp(context, entry.packageName) }
                )
            }
        }
    }
}

@Composable
private fun InboxConversationCard(
    entry: InboxEntry,
    onRead: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RectangleShape)
            .border(2.dp, MaterialTheme.colorScheme.outline)
            .clickable {
                onRead()
                onOpen()
            }
    ) {
        Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            NexusAvatar(entry, Modifier.size(48.dp))
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (entry.unread) "[*]" else "[ ]", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                Text(entry.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.source} · ${timeLabel(entry.time)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Text("PREVIEW", color = MaterialTheme.colorScheme.tertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onOpenUrl: (String, String) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { KatoDatabase(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var shelf by rememberSaveable { mutableStateOf("Saved") }
    var category by rememberSaveable { mutableStateOf("All") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val categories = listOf("All", "Article", "Video", "Movie", "Music", "Podcast", "Learning")
    val records = remember(refresh, search, shelf, category) {
        when (shelf) {
            "Liked" -> database.listItems(search = search, likedOnly = true)
            "Watch later" -> database.listItems(search = search).filter { !it.completed }
            else -> database.listItems(search = search, type = category)
        }.let { values -> if (shelf == "Watch later") values.filter { !it.completed } else values }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PERSONAL LIBRARY", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Keep what matters.", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onOpenSettings) { Text("⚙ SETTINGS") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Saved", "Watch later", "Liked").forEach { option ->
                FilterChip(selected = shelf == option, onClick = { shelf = option }, label = { Text(option, fontSize = 10.sp) })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) { Text("+ SAVE", fontSize = 10.sp) }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search your library") }
        )
        if (shelf == "Saved") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { choice ->
                    FilterChip(selected = category == choice, onClick = { category = choice }, label = { Text(choice, fontSize = 9.sp) })
                }
            }
        }
        if (records.isEmpty()) {
            QuietCard(
                title = when (shelf) {
                    "Liked" -> "Nothing liked yet"
                    "Watch later" -> "Your watch-later list is empty"
                    else -> "Your library is ready"
                },
                detail = "Save a story or video from Social, add a link manually, or like a post. Everything stays on this device."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(records, key = { it.id }) { item ->
                    SavedItemCard(
                        item = item,
                        onOpen = { onOpenUrl(item.url, item.title) },
                        onComplete = {
                            database.setCompleted(item.id, !item.completed)
                            refresh++
                        },
                        onLike = {
                            database.setLiked(item.id, !item.liked)
                            refresh++
                        },
                        onDelete = {
                            database.deleteItem(item.id)
                            refresh++
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        SaveItemDialog(
            onDismiss = { showAdd = false },
            onSave = { title, url, type, note ->
                database.saveItem(title, normalizeUrl(url), type, note)
                showAdd = false
                refresh++
            }
        )
    }
}

@Composable
private fun SavedItemCard(
    item: SavedItem,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.type.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                if (item.completed) Text("COMPLETED", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                if (item.liked) Text("  ♥", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.note.isNotBlank()) Text(item.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TextButton(onClick = onOpen) { Text("OPEN", fontSize = 9.sp) }
                TextButton(onClick = onComplete) { Text(if (item.completed) "REOPEN" else "DONE", fontSize = 9.sp) }
                TextButton(onClick = onLike) { Text(if (item.liked) "UNLIKE" else "LIKE", fontSize = 9.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("REMOVE", color = MaterialTheme.colorScheme.error, fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun NexusSettingsScreen(
    themeMode: String,
    topics: Set<String>,
    channelText: String,
    customFeedText: String,
    spotifyUrl: String,
    onBack: () -> Unit,
    onThemeChange: (String) -> Unit,
    onTopicsChange: (Set<String>) -> Unit,
    onChannelsChange: (String) -> Unit,
    onCustomFeedsChange: (String) -> Unit,
    onSpotifyChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { KatoDatabase(context) }
    var localChannels by remember(channelText) { mutableStateOf(channelText) }
    var localFeeds by remember(customFeedText) { mutableStateOf(customFeedText) }
    var localSpotify by remember(spotifyUrl) { mutableStateOf(spotifyUrl) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NEXUS SETTINGS", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("Make the feed yours.", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onBack) { Text("DONE") }
            }
        }
        item {
            SettingsCard("YOUR TOPICS", "Only selected topics influence the news selection.") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    topicOptions.chunked(3).forEach { rowTopics ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            rowTopics.forEach { topic ->
                                FilterChip(
                                    selected = topic in topics,
                                    onClick = {
                                        val next = topics.toMutableSet()
                                        if (topic in next) next.remove(topic) else next.add(topic)
                                        if (next.isNotEmpty()) onTopicsChange(next)
                                    },
                                    label = { Text(topic, fontSize = 9.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsCard("YOUTUBE CHANNELS", "Add one channel ID per line. You can paste a /channel/UC… URL. A label is optional: UC… | Khan Academy.") {
                OutlinedTextField(
                    value = localChannels,
                    onValueChange = { localChannels = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("UC... | Channel name") }
                )
                Button(onClick = { onChannelsChange(localChannels); onRefresh() }, modifier = Modifier.fillMaxWidth()) { Text("SAVE FOLLOWED CHANNELS") }
                Text("Channel handles such as @name need resolving to a channel ID first. Nexus uses public channel upload feeds; no YouTube API key is needed for this feed method.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
        item {
            SettingsCard("SPOTIFY PLAYLIST / ALBUM", "Paste a Spotify track, album or playlist URL. The official Spotify embed opens inside Nexus.") {
                OutlinedTextField(
                    value = localSpotify,
                    onValueChange = { localSpotify = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://open.spotify.com/playlist/...") }
                )
                Button(onClick = { onSpotifyChange(localSpotify.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("SAVE SPOTIFY LINK") }
                Text("Spotify controls its own playback, account requirements and any ads. Nexus cannot remove those restrictions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
        item {
            SettingsCard("EXTRA NEWS SOURCES", "Optional RSS or Atom feed URLs, one per line.") {
                OutlinedTextField(
                    value = localFeeds,
                    onValueChange = { localFeeds = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("https://example.com/feed.xml") }
                )
                Button(onClick = { onCustomFeedsChange(localFeeds); onRefresh() }, modifier = Modifier.fillMaxWidth()) { Text("SAVE RSS SOURCES") }
            }
        }
        item {
            SettingsCard("APPEARANCE", "Keep the interface comfortable.") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("System", "Dark", "Light").forEach { option ->
                        FilterChip(selected = themeMode == option, onClick = { onThemeChange(option) }, label = { Text(option) })
                    }
                }
            }
        }
        item {
            SettingsCard("MESSAGE PREVIEWS", "Inbox previews remain local to this device. You control the Android permission.") {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("OPEN ANDROID NOTIFICATION ACCESS") }
            }
        }
        item {
            SettingsCard("FREE & PRIVATE BY DESIGN", "Nexus does not include an advertising SDK, paid feature tier, account system or required backend. Some third-party media players may still show platform-controlled ads.") {
                Text("News and followed-channel data are fetched directly from public RSS / Atom feeds. Your saved items and preferences stay in the app's private local storage.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
                Button(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, database.exportJson())
                        }
                        context.startActivity(Intent.createChooser(share, "Export Nexus library"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("EXPORT LIBRARY DATA") }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("CLEAR LOCAL CONTENT") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local content?") },
            text = { Text("This removes saved items and message previews from this device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    database.clearItems()
                    context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE)
                        .edit().remove("kato_notifications").apply()
                    confirmClear = false
                }) { Text("CLEAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun VideoTile(video: NexusVideo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.shadow(4.dp, RectangleShape).border(2.dp, MaterialTheme.colorScheme.outline).clickable(onClick = onClick),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            NexusRemoteImage(video.thumbnail, Modifier.fillMaxWidth().height(126.dp), video.title)
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("[>] VIDEO · FOLLOWED", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                Text(video.channel.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(video.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(dateLabel(video.publishedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MovieTile(movie: NexusMovie, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.shadow(4.dp, RectangleShape).border(2.dp, MaterialTheme.colorScheme.outline).clickable(onClick = onClick),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            NexusRemoteImage(movie.thumbnail, Modifier.fillMaxWidth().height(190.dp), movie.title)
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("[>] PUBLIC DOMAIN", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                Text(movie.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(movie.year, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun NewsCard(story: NexusNews, compact: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RectangleShape).border(2.dp, MaterialTheme.colorScheme.outline).clickable(onClick = onClick),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            if (story.imageUrl.isNotBlank()) {
                NexusRemoteImage(story.imageUrl, Modifier.fillMaxWidth().height(if (compact) 112.dp else 176.dp), story.title)
            } else {
                Box(
                    Modifier.fillMaxWidth().height(if (compact) 112.dp else 176.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+-- NEWS IMAGE UNAVAILABLE --+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("[>] NEWS · ${story.topic.uppercase(Locale.ROOT)}", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(story.source.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(story.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(dateLabel(story.publishedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun NexusAvatar(entry: InboxEntry, modifier: Modifier = Modifier) {
    val bitmap = remember(entry.avatar) {
        try {
            if (entry.avatar.isBlank()) null else {
                val bytes = Base64.decode(entry.avatar, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = entry.title, modifier = modifier.clip(RectangleShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RectangleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(entry.title.take(1).uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

private object NexusImageStore {
    private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024

    fun load(context: Context, rawUrl: String): Bitmap? {
        if (rawUrl.isBlank()) return null
        val uri = try { Uri.parse(rawUrl) } catch (_: Exception) { return null }
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        val directory = File(context.cacheDir, "nexus_images").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256")
            .digest(rawUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val file = File(directory, "$key.img")
        if (!file.exists() || file.length() == 0L) {
            val temp = File(directory, "$key.tmp")
            try {
                val connection = (URL(rawUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NexusAndroid/1.0")
                    setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                }
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_IMAGE_BYTES) throw IllegalStateException("Image too large")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                connection.disconnect()
                if (temp.length() > 0L) temp.renameTo(file) else temp.delete()
            } catch (_: Exception) {
                temp.delete()
                return null
            }
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        } catch (_: Exception) { null }
    }
}

@Composable
private fun NexusRemoteImage(url: String, modifier: Modifier = Modifier, description: String? = null) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            NexusImageStore.load(context, url)
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            Text("[ image ]", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MediaPlayerScreen(
    type: String,
    title: String,
    target: String,
    videoId: String,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← BACK") }
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when (type) {
                "YouTube" -> "Official YouTube player · no autoplay"
                "Spotify" -> "Official Spotify embed"
                "Movie" -> "Internet Archive source page"
                else -> "Source page"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        NexusWebView(
            type = type,
            target = target,
            videoId = videoId,
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RectangleShape)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NexusWebView(type: String, target: String, videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
                        if (NexusAdBlocker.shouldBlock(uri)) {
                            return android.webkit.WebResourceResponse(
                                "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        return uri.scheme !in listOf("https", "http")
                    }
                }
                when (type) {
                    "YouTube" -> {
                        val html = """
                            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                            <body style="margin:0;background:#000;display:flex;align-items:center;min-height:100vh">
                            <iframe style="width:100%;aspect-ratio:16/9;border:0" src="https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0"
                            title="YouTube video player" allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
                            </body></html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                    }
                    else -> loadUrl(target)
                }
            }
        },
        update = { }
    )
}

private object NexusAdBlocker {
    // A lightweight local blocklist for common ad, analytics and tracking hosts.
    // This is not a VPN-level filter and cannot reliably remove first-party ads.
    private val blockedHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "adform.net",
        "taboola.com", "outbrain.com", "criteo.com", "scorecardresearch.com",
        "quantserve.com", "hotjar.com", "fullstory.com", "clarity.ms",
        "segment.io", "segment.com", "amplitude.com", "mixpanel.com",
        "app-measurement.com", "branch.io", "adjust.com", "unityads.unity3d.com",
        "applovin.com", " ironsource.com", "inmobi.com"
    )

    fun shouldBlock(uri: Uri): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return blockedHosts.any { host == it.trim() || host.endsWith("." + it.trim()) }
    }
}

@Composable
private fun SettingsCard(title: String, detail: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
            content()
        }
    }
}

@Composable
private fun QuietCard(title: String, detail: String) {
    Card(shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun SaveItemDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Article") }
    var error by rememberSaveable { mutableStateOf(false) }
    val types = listOf("Article", "Video", "Movie", "Music", "Podcast", "Learning")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Nexus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it; error = false }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it; error = false }, label = { Text("Original link") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.forEach { choice ->
                        FilterChip(selected = type == choice, onClick = { type = choice }, label = { Text(choice, fontSize = 8.sp) })
                    }
                }
                if (error) Text("Enter a title and valid web link.", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val safeUrl = normalizeUrl(url)
                if (title.isNotBlank() && safeUrl.startsWith("https://")) onSave(title.trim(), safeUrl, type, note.trim()) else error = true
            }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

private fun readInbox(context: Context): List<InboxEntry> {
    return try {
        val prefs = context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString("kato_notifications", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            InboxEntry(
                id = item.optString("id", item.optString("source") + "|" + item.optString("title")),
                source = item.optString("source", "Message"),
                title = item.optString("title", "Conversation"),
                message = item.optString("message", ""),
                time = item.optLong("time", 0L),
                unread = item.optBoolean("unread", true),
                avatar = item.optString("avatar", ""),
                packageName = item.optString("packageName", "")
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun setInboxRead(context: Context, id: String) {
    try {
        val prefs = context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString("kato_notifications", "[]"))
        val next = JSONArray()
        for (index in 0 until old.length()) {
            val item = old.optJSONObject(index) ?: continue
            if (item.optString("id") == id) item.put("unread", false)
            next.put(item)
        }
        prefs.edit().putString("kato_notifications", next.toString()).apply()
    } catch (_: Exception) { }
}

private fun launchMessagingApp(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun normalizeUrl(raw: String): String {
    val clean = raw.trim()
    return when {
        clean.startsWith("https://", true) -> clean
        clean.startsWith("http://", true) -> "https://" + clean.removePrefix("http://")
        clean.contains(".") && !clean.contains(" ") -> "https://$clean"
        else -> clean
    }
}

private fun spotifyEmbedUrl(raw: String): String {
    val uri = try { Uri.parse(raw.trim()) } catch (_: Exception) { return "" }
    val parts = uri.pathSegments
    if (uri.host?.contains("spotify.com", true) != true || parts.size < 2) return ""
    val type = parts[0]
    val id = parts[1]
    if (type !in listOf("playlist", "album", "track", "episode", "show") || id.isBlank()) return ""
    return "https://open.spotify.com/embed/$type/$id?utm_source=generator&theme=0"
}

private fun openExternal(context: Context, rawUrl: String) {
    val url = normalizeUrl(rawUrl)
    if (!url.startsWith("https://")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) { }
}

private fun dateLabel(time: Long): String {
    if (time <= 0L) return "RECENT"
    return try { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time)) } catch (_: Exception) { "RECENT" }
}

private fun timeLabel(time: Long): String {
    if (time <= 0L) return ""
    return try { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)) } catch (_: Exception) { "" }
}
