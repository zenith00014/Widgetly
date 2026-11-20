package com.widgetly.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Aubergine = Color(0xFF14101F)
private val SurfaceDark = Color(0xFF211A30)
private val SurfaceRaised = Color(0xFF30243F)
private val Paper = Color(0xFFF6F1FB)
private val Muted = Color(0xFFBDB3CA)
private val Magenta = Color(0xFFFF3D81)
private val Mint = Color(0xFF7CF5D0)
private val Lilac = Color(0xFFE9E7F1)

private data class MediaItem(
    val id: String,
    val title: String,
    val creator: String,
    val type: String,
    val art: List<Color>,
    val note: String
)

private data class FeedPost(
    val id: String,
    val author: String,
    val handle: String,
    val avatar: String,
    val time: String,
    val message: String,
    val mediaId: String? = null,
    val likes: String = "—",
    val replies: String = "—"
)

private val mediaCatalog = listOf(
    MediaItem("v1", "The craft behind a great scene", "Frame by Frame", "VIDEO", listOf(Color(0xFF355C7D), Color(0xFF6C5B7B)), "Video · YouTube"),
    MediaItem("v2", "A field recording from the coast", "Open Ear", "VIDEO", listOf(Color(0xFF27645F), Color(0xFF83B6A2)), "Video · YouTube"),
    MediaItem("v3", "How animation finds its rhythm", "Motion Notes", "VIDEO", listOf(Color(0xFF9C4F43), Color(0xFFE5A45B)), "Video · YouTube"),
    MediaItem("m1", "Late Night Frequencies", "Mira Sol", "MUSIC", listOf(Color(0xFF8D3C9C), Color(0xFFFF6C83)), "Album · Audio mode"),
    MediaItem("m2", "Blue Hour Tapes", "Niko Vale", "MUSIC", listOf(Color(0xFF235D68), Color(0xFF79B5A8)), "Playlist · Audio mode"),
    MediaItem("m3", "Soft Focus", "The Sunday Club", "MUSIC", listOf(Color(0xFF4C477F), Color(0xFFB7A4E8)), "Mix · Audio mode")
)

private val feedPosts = listOf(
    FeedPost("p1", "Frame by Frame", "@framebyframe", "F", "12m", "A good video doesn't need to shout. This breakdown looks at the tiny details that make a scene stay with you.", "v1", "248", "18"),
    FeedPost("p2", "Mira Sol", "@mirasol", "M", "38m", "Putting the headphones on and letting the whole record play. No skips tonight.", "m1", "1.2k", "64"),
    FeedPost("p3", "Open Ear", "@openear", "O", "1h", "A little sound from the edge of the map. Headphones recommended.", "v2", "96", "11"),
    FeedPost("p4", "Motion Notes", "@motionnotes", "N", "2h", "The best animation often lives in the pauses between movements.", "v3", "532", "27"),
    FeedPost("p5", "Niko Vale", "@nikovale", "N", "4h", "Blue hour, warm speakers, and nowhere else to be.", "m2", "807", "42")
)

private data class NewsStory(val title: String, val source: String, val time: String, val summary: String)

private val newsStories = listOf(
    NewsStory("Independent creators are building smaller, closer communities", "CULTURE", "18 MIN AGO", "A shift toward direct relationships is changing how creators publish, collaborate and bring audiences into their work."),
    NewsStory("The new language of music discovery", "SOUND", "42 MIN AGO", "Curators, niche radio and thoughtful recommendations are giving listeners alternatives to endless algorithmic playlists."),
    NewsStory("Why short-form video is learning to slow down", "DIGITAL", "1 HR AGO", "Some creators are making room for context, craft and complete ideas instead of chasing the next quick hit.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RushApp(getSharedPreferences("rush_prefs", Context.MODE_PRIVATE)) }
    }
}

@Composable
private fun RushApp(prefs: android.content.SharedPreferences) {
    var tab by remember { mutableStateOf("Home") }
    var homeMode by remember { mutableStateOf("FOR YOU") }
    var playerItem by remember { mutableStateOf<MediaItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(prefs.getBoolean("light", false)) }
    var query by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(prefs.getStringSet("saved", emptySet())?.toSet() ?: emptySet()) }
    var history by remember { mutableStateOf(prefs.getStringSet("history", emptySet())?.toSet() ?: emptySet()) }
    var liked by remember { mutableStateOf(prefs.getStringSet("liked", emptySet())?.toSet() ?: emptySet()) }
    var draft by remember { mutableStateOf("") }
    var localPosts by remember { mutableStateOf(emptyList<FeedPost>()) }

    val bg = if (light) Lilac else Aubergine
    val panel = if (light) Color.White else SurfaceDark
    val text = if (light) Color(0xFF241B32) else Paper
    val muted = if (light) Color(0xFF70677D) else Muted

    MaterialTheme(
        colorScheme = if (light) lightColorScheme(primary = Magenta, background = bg, surface = panel, onSurface = text)
        else darkColorScheme(primary = Magenta, background = bg, surface = panel, onSurface = text)
    ) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.fillMaxSize()) {
                if (playerItem != null) {
                    PlayerScreen(playerItem!!, playing, { playing = !playing }, { playerItem = null }, light)
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("R U S H", color = if (light) Magenta else Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                            Text(
                                when (tab) {
                                    "Videos" -> "Watch what matters"
                                    "Music" -> "Listen your way"
                                    "News" -> "A little context"
                                    "Library" -> "Your collection"
                                    else -> "Your daily signal"
                                },
                                color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { light = !light; prefs.edit().putBoolean("light", light).apply() }) {
                            Text(if (light) "DARK ◐" else "LIGHT ◑", color = muted, fontSize = 10.sp)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            "Videos" -> MediaFeed("VIDEO", query, { query = it }, text, muted, panel, saved,
                                { openMedia(it, { playerItem = it; playing = true }, { history = history + it.id; prefs.edit().putStringSet("history", history).apply() }) },
                                { id -> saved = toggleSet(saved, id); prefs.edit().putStringSet("saved", saved).apply() })
                            "Music" -> MediaFeed("MUSIC", query, { query = it }, text, muted, panel, saved,
                                { openMedia(it, { playerItem = it; playing = true }, { history = history + it.id; prefs.edit().putStringSet("history", history).apply() }) },
                                { id -> saved = toggleSet(saved, id); prefs.edit().putStringSet("saved", saved).apply() })
                            "News" -> NewsScreen(text, muted, panel)
                            "Library" -> LibraryScreen(saved, history, text, muted, panel,
                                { id -> mediaCatalog.find { it.id == id }?.let { playerItem = it; playing = true } },
                                { id -> saved = saved - id; prefs.edit().putStringSet("saved", saved).apply() })
                            else -> HomeFeed(
                                homeMode, { homeMode = it }, draft, { draft = it },
                                localPosts + feedPosts, text, muted, panel, saved, liked,
                                onPost = {
                                    if (draft.isNotBlank()) {
                                        localPosts = listOf(FeedPost("local-${System.currentTimeMillis()}", "You", "@you", "Y", "NOW", draft.trim(), likes = "0", replies = "0")) + localPosts
                                        draft = ""
                                    }
                                },
                                onPlay = { item -> playerItem = item; playing = true; history = history + item.id; prefs.edit().putStringSet("history", history).apply() },
                                onSave = { id -> saved = toggleSet(saved, id); prefs.edit().putStringSet("saved", saved).apply() },
                                onLike = { id -> liked = toggleSet(liked, id); prefs.edit().putStringSet("liked", liked).apply() }
                            )
                        }
                    }
                    val recentItem = mediaCatalog.firstOrNull { it.id in history }
                    if (recentItem != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(15.dp)).background(panel).clickable { playerItem = recentItem; playing = true }
                                .padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Artwork(recentItem, Modifier.size(40.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("RECENTLY PLAYED", fontSize = 9.sp, color = muted, letterSpacing = 1.sp)
                                Text(recentItem.title, fontSize = 13.sp, color = text, fontWeight = FontWeight.SemiBold)
                            }
                            Text("▶", color = Mint, fontSize = 17.sp)
                        }
                    }
                    NavigationBar(containerColor = panel, contentColor = text) {
                        listOf("Home", "Videos", "Music", "News", "Library").forEach { destination ->
                            NavigationBarItem(
                                selected = tab == destination,
                                onClick = { tab = destination; query = "" },
                                icon = { Text(when (destination) { "Home" -> "⌂"; "Videos" -> "▷"; "Music" -> "♫"; "News" -> "▤"; else -> "▣" }, fontSize = 18.sp) },
                                label = { Text(destination, fontSize = 9.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Magenta, selectedTextColor = Magenta,
                                    indicatorColor = if (light) Color(0xFFF8DCE8) else SurfaceRaised,
                                    unselectedIconColor = muted, unselectedTextColor = muted
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toggleSet(set: Set<String>, id: String) = if (id in set) set - id else set + id

private fun openMedia(item: MediaItem, open: (MediaItem) -> Unit, record: () -> Unit) {
    open(item)
    record()
}

@Composable
private fun HomeFeed(
    mode: String, onMode: (String) -> Unit, draft: String, onDraft: (String) -> Unit,
    posts: List<FeedPost>, text: Color, muted: Color, panel: Color,
    saved: Set<String>, liked: Set<String>, onPost: () -> Unit,
    onPlay: (MediaItem) -> Unit, onSave: (String) -> Unit, onLike: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("FOR YOU", "FOLLOWING").forEach { option ->
                    Column(Modifier.weight(1f).clickable { onMode(option) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(option, color = if (mode == option) text else muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (mode == option) Magenta else Color.Transparent))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Avatar("Y", panel, Mint)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    BasicTextField(
                        value = draft, onValueChange = onDraft,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(13.dp)).background(panel).padding(13.dp),
                        textStyle = TextStyle(color = text, fontSize = 14.sp),
                        decorationBox = { inner -> if (draft.isEmpty()) Text("Share something worth a moment…", color = muted, fontSize = 14.sp); inner() }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text("POST", color = if (draft.isBlank()) muted else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(if (draft.isBlank()) panel else Magenta).clickable(enabled = draft.isNotBlank()) { onPost() }.padding(horizontal = 18.dp, vertical = 9.dp))
                    }
                }
            }
        }
        items(posts.filterIndexed { index, _ -> mode == "FOR YOU" || index % 2 == 0 }, key = { it.id }) { post ->
            FeedPostCard(post, text, muted, panel, saved, liked, onPlay, onSave, onLike)
            HorizontalDivider(color = muted.copy(alpha = .14f), thickness = 0.6.dp)
        }
        item { Text("YOU'RE UP TO DATE.", color = muted, fontSize = 9.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth().padding(22.dp)) }
    }
}

@Composable
private fun FeedPostCard(
    post: FeedPost, text: Color, muted: Color, panel: Color, saved: Set<String>, liked: Set<String>,
    onPlay: (MediaItem) -> Unit, onSave: (String) -> Unit, onLike: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.avatar, panel, Magenta)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(post.author, color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${post.handle} · ${post.time}", color = muted, fontSize = 10.sp)
            }
            Text("•••", color = muted, fontSize = 14.sp)
        }
        Text(post.message, color = text, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 42.dp, top = 9.dp, bottom = 10.dp))
        post.mediaId?.let { id ->
            mediaCatalog.find { it.id == id }?.let { media ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 42.dp).clip(RoundedCornerShape(16.dp)).background(panel)
                        .clickable { onPlay(media) }.padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(media, Modifier.size(82.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(media.type, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(media.title, color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(media.creator, color = muted, fontSize = 10.sp)
                        Text("OPEN IN RUSH  →", color = Magenta, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 42.dp, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("♡  ${post.replies}", color = muted, fontSize = 10.sp, modifier = Modifier.clickable { onLike(post.id) })
                    Text(if (post.id in liked) "♥  LIKED" else "♡  ${post.likes}", color = if (post.id in liked) Magenta else muted, fontSize = 10.sp, modifier = Modifier.clickable { onLike(post.id) })
                    Text(if (id in saved) "SAVED" else "＋ SAVE", color = if (id in saved) Magenta else muted, fontSize = 10.sp, modifier = Modifier.clickable { onSave(id) })
                    Text("↗  SHARE", color = muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MediaFeed(
    type: String, query: String, onQuery: (String) -> Unit, text: Color, muted: Color, panel: Color,
    saved: Set<String>, onPlay: (MediaItem) -> Unit, onSave: (String) -> Unit
) {
    val results = mediaCatalog.filter { it.type == type && (it.title + " " + it.creator).contains(query, true) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(panel).padding(15.dp),
                textStyle = TextStyle(color = text, fontSize = 14.sp),
                decorationBox = { inner -> if (query.isEmpty()) Text(if (type == "MUSIC") "Search music and artists…" else "Search videos and creators…", color = muted); inner() }
            )
        }
        item {
            Text(if (type == "MUSIC") "LISTEN WITHOUT THE VIDEO" else "VIDEO CHANNELS & PICKS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        }
        items(results, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(item, Modifier.size(88.dp).clickable { onPlay(item) })
                Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.type, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(item.title, color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.creator, color = muted, fontSize = 11.sp)
                    Text(item.note, color = muted, fontSize = 9.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (item.id in saved) "♥" else "＋", color = if (item.id in saved) Magenta else muted, fontSize = 18.sp, modifier = Modifier.clickable { onSave(item.id) }.padding(5.dp))
                    Text("▶", color = Magenta, fontSize = 16.sp, modifier = Modifier.clickable { onPlay(item) }.padding(5.dp))
                }
            }
        }
        if (results.isEmpty()) item { Text("No matches in this shelf.", color = muted, modifier = Modifier.padding(18.dp)) }
        item { Text("END OF PICKS. MORE WHEN YOU'RE READY.", color = muted, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 12.dp)) }
    }
}

@Composable
private fun NewsScreen(text: Color, muted: Color, panel: Color) {
    var expanded by remember { mutableIntStateOf(-1) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("THE HEADLINES, WITHOUT THE NOISE.", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
        items(newsStories.size) { index ->
            val story = newsStories[index]
            Card(
                colors = CardDefaults.cardColors(containerColor = panel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().clickable { expanded = if (expanded == index) -1 else index }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${story.source}  ·  ${story.time}", color = Magenta, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(story.title, color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                    if (expanded == index) Text(story.summary, color = muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Text(if (expanded == index) "COLLAPSE  ↑" else "READ SUMMARY  ↓", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("YOU'RE ALL CAUGHT UP.", color = muted, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 12.dp)) }
    }
}

@Composable
private fun LibraryScreen(saved: Set<String>, history: Set<String>, text: Color, muted: Color, panel: Color, onPlay: (String) -> Unit, onRemove: (String) -> Unit) {
    val savedItems = mediaCatalog.filter { it.id in saved }
    val recentItems = mediaCatalog.filter { it.id in history }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { SectionTitle("SAVED FOR LATER", "${savedItems.size} ITEMS", muted) }
        if (savedItems.isEmpty()) item { EmptyCard("Your saved shelf is empty. Save a video or track to keep it here.", panel, muted) }
        items(savedItems, key = { it.id }) { item -> MediaRow(item, text, muted, panel, { onPlay(item.id) }, { onRemove(item.id) }, true) }
        item { SectionTitle("RECENTLY PLAYED", "${recentItems.size} ITEMS", muted) }
        if (recentItems.isEmpty()) item { EmptyCard("Videos and tracks you play will show up here.", panel, muted) }
        items(recentItems, key = { it.id }) { item -> MediaRow(item, text, muted, panel, { onPlay(item.id) }, {}, false) }
    }
}

@Composable
private fun PlayerScreen(item: MediaItem, playing: Boolean, onToggle: () -> Unit, onBack: () -> Unit, light: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(item.id) { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
    var source by remember(item.id) { mutableStateOf("") }
    var audioOnly by remember(item.id) { mutableStateOf(item.type == "MUSIC") }
    var status by remember(item.id) { mutableStateOf("Add a YouTube link or video ID to start playback.") }
    val text = if (light) Color(0xFF241B32) else Paper
    val muted = if (light) Color(0xFF70677D) else Muted
    val panel = if (light) Color.White else SurfaceDark

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(playing) { player.playWhenReady = playing }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("← BACK", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
            Text(if (audioOnly) "AUDIO MODE" else "VIDEO MODE", color = Mint, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("RUSH", color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        if (audioOnly) {
            Artwork(item, Modifier.fillMaxWidth().weight(1f).aspectRatio(1f))
            Text(item.title, color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(item.creator, color = muted, fontSize = 12.sp)
        } else {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { this.player = player; useController = true; showBuffering = androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
            )
            Text(item.title, color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(item.creator, color = muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !audioOnly, onClick = { audioOnly = false }, label = { Text("VIDEO") })
            FilterChip(selected = audioOnly, onClick = { audioOnly = true }, label = { Text("AUDIO ONLY") })
        }
        BasicTextField(
            value = source, onValueChange = { source = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(panel).padding(13.dp),
            textStyle = TextStyle(color = text, fontSize = 13.sp),
            decorationBox = { inner -> if (source.isEmpty()) Text("YouTube URL or 11-character video ID", color = muted); inner() }
        )
        Button(
            onClick = {
                scope.launch {
                    status = "Resolving YouTube stream…"
                    try {
                        val streamUrl = withContext(Dispatchers.IO) { LibreTubePlayback.resolve(source, audioOnly) }
                        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(streamUrl))
                        player.prepare()
                        player.play()
                        status = if (audioOnly) "AUDIO STREAM READY" else "VIDEO STREAM READY"
                    } catch (error: Exception) {
                        status = error.localizedMessage ?: "Unable to load this YouTube stream."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Magenta)
        ) { Text("LOAD YOUTUBE PLAYBACK", color = Color.White, fontWeight = FontWeight.Bold) }
        if (audioOnly) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { this.player = player; useController = true } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(76.dp)
            )
        }
        Text(status, color = muted, fontSize = 10.sp)
        Text("Playback depends on YouTube availability and extractor compatibility.", color = muted, fontSize = 9.sp)
    }
}

@Composable
private fun MediaRow(item: MediaItem, text: Color, muted: Color, panel: Color, onPlay: () -> Unit, onSave: () -> Unit, isSaved: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(panel).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(item, Modifier.size(76.dp).clickable { onPlay() })
        Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.type, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(item.title, color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.creator, color = muted, fontSize = 11.sp)
            Text(item.note, color = muted, fontSize = 9.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isSaved) "♥" else "＋", color = if (isSaved) Magenta else muted, fontSize = 18.sp, modifier = Modifier.clickable { onSave() }.padding(5.dp))
            Text("▶", color = Magenta, fontSize = 15.sp, modifier = Modifier.clickable { onPlay() }.padding(5.dp))
        }
    }
}

@Composable
private fun Avatar(initial: String, panel: Color, accent: Color) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, panel))), contentAlignment = Alignment.Center) {
        Text(initial.take(1), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Artwork(item: MediaItem, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(item.art)), contentAlignment = Alignment.Center) {
        Text(if (item.type == "MUSIC") "♫" else "▷", color = Color.White.copy(alpha = .9f), fontSize = 42.sp, fontWeight = FontWeight.Light)
        Text(item.title.take(1).uppercase(), color = Color.White.copy(alpha = .2f), fontSize = 76.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SectionTitle(title: String, count: String, muted: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(count, color = muted, fontSize = 9.sp)
    }
}

@Composable
private fun EmptyCard(message: String, panel: Color, muted: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
        Text(message, color = muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}
