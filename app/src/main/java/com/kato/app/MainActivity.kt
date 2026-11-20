package com.kato.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KatoInk = Color(0xFF171A17)
private val KatoPanel = Color(0xFF232923)
private val KatoPanelRaised = Color(0xFF2B332B)
private val KatoLime = Color(0xFFD2E49A)
private val KatoAmber = Color(0xFFE4BD73)
private val KatoPaper = Color(0xFFF2F0E7)
private val KatoMuted = Color(0xFF9BA397)

private val KatoDarkColors = darkColorScheme(
    primary = KatoLime,
    onPrimary = KatoInk,
    secondary = KatoAmber,
    onSecondary = KatoInk,
    background = KatoInk,
    onBackground = KatoPaper,
    surface = KatoPanel,
    onSurface = KatoPaper,
    surfaceVariant = KatoPanelRaised,
    onSurfaceVariant = KatoMuted
)

private val KatoLightColors = lightColorScheme(
    primary = Color(0xFF596B3D),
    onPrimary = Color.White,
    secondary = Color(0xFF87602C),
    onSecondary = Color.White,
    background = Color(0xFFF1F0E8),
    onBackground = Color(0xFF20251F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF20251F),
    surfaceVariant = Color(0xFFE6E8DE),
    onSurfaceVariant = Color(0xFF626B5E)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KatoApp() }
    }
}

@Composable
private fun KatoApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "Dark") ?: "Dark") }
    var page by rememberSaveable { mutableStateOf("Home") }
    var discoverSection by rememberSaveable { mutableStateOf("Brief") }
    var refresh by remember { mutableIntStateOf(0) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "Light" -> false
        "System" -> systemDark
        else -> true
    }

    MaterialTheme(colorScheme = if (useDark) KatoDarkColors else KatoLightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "KATO",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = if (page == "Settings") "Preferences and privacy" else "Your personal media console",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "●",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    TextButton(onClick = { page = if (page == "Settings") "Home" else "Settings" }) {
                        Text(if (page == "Settings") "BACK" else "⚙", fontWeight = FontWeight.Bold)
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (page) {
                        "Settings" -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                prefs.edit().putString("theme_mode", it).apply()
                            },
                            onDataChanged = { refresh++ }
                        )
                        "Social" -> SocialScreen(refresh = refresh)
                        "Discover" -> DiscoverScreen(section = discoverSection, onSectionChange = { discoverSection = it })
                        "Library" -> LibraryScreen(refresh = refresh, onDataChanged = { refresh++ })
                        else -> HomeScreen(
                            refresh = refresh,
                            onOpenSocial = { page = "Social" },
                            onOpenDiscover = {
                                discoverSection = it
                                page = "Discover"
                            },
                            onOpenLibrary = { page = "Library" }
                        )
                    }
                }

                if (page != "Settings") {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        val tabs = listOf(
                            Triple("Home", "⌂", "HOME"),
                            Triple("Social", "▤", "SOCIAL"),
                            Triple("Discover", "◉", "DISCOVER"),
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
private fun HomeScreen(
    refresh: Int,
    onOpenSocial: () -> Unit,
    onOpenDiscover: (String) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { KatoDatabase(context) }
    val saved = remember(refresh) { database.listItems() }
    val notifications = remember(refresh) { KatoNotifications.read(context) }
    val unfinished = saved.filter { !it.completed }.take(2)
    val dateLabel = remember { SimpleDateFormat("EEEE  •  d MMMM", Locale.getDefault()).format(Date()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(dateLabel.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Text("Your space.\nYour pace.", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                        Text("No endless feed. Just what you choose.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("K", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 25.sp)
                        }
                    }
                }
            }
        }

        item { SectionHeading("COMMUNICATION", "Your conversations, not a social feed") }
        item {
            DashboardActionCard(
                title = "Unified inbox",
                detail = if (notifications.isEmpty()) "No notification previews captured" else notifications.size.toString() + " recent notification previews",
                action = "OPEN INBOX",
                accent = MaterialTheme.colorScheme.primary,
                onClick = onOpenSocial
            )
        }

        item { SectionHeading("TODAY'S BRIEF", "A small briefing with a clear finish") }
        item {
            DashboardActionCard(
                title = "Nothing queued yet",
                detail = "Your news sources and followed channels will appear here when connected. Kato will not invent a briefing.",
                action = "EXPLORE DISCOVER",
                accent = MaterialTheme.colorScheme.secondary,
                onClick = { onOpenDiscover("Brief") }
            )
        }

        item { SectionHeading("CONTINUE", "Pick up where you left off") }
        if (unfinished.isEmpty()) {
            item {
                QuietCard(
                    title = "Your listening and watch history starts here",
                    detail = "Save an article, video, film or podcast to your Library. Progress tracking will be added with the media players."
                )
            }
        } else {
            items(unfinished) { item ->
                SavedItemCard(
                    item = item,
                    onOpen = { openExternal(context, item.url) },
                    onComplete = { database.setCompleted(item.id, true) },
                    onDelete = { database.deleteItem(item.id) }
                )
            }
        }

        item { SectionHeading("QUICK ACCESS", "Choose an activity") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickAccessCard("♫", "Music", "Local audio", Modifier.weight(1f)) { onOpenLibrary() }
                    QuickAccessCard("▷", "Watch", "Selected videos", Modifier.weight(1f)) { onOpenDiscover("YouTube") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickAccessCard("▤", "Read", "Articles", Modifier.weight(1f)) { onOpenDiscover("Articles") }
                    QuickAccessCard("▣", "Movies", "Free and legal", Modifier.weight(1f)) { onOpenDiscover("Movies") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    detail: String,
    action: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            Text(action, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun QuickAccessCard(glyph: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(glyph, color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp)
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SocialScreen(refresh: Int) {
    val context = LocalContext.current
    val enabled = remember(refresh) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val allEntries = remember(refresh) { KatoNotifications.read(context) }
    var filter by rememberSaveable { mutableStateOf("All") }
    val entries = if (filter == "All") allEntries else allEntries.filter { it.source == filter }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MESSAGE PREVIEWS", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text(
            "Kato's inbox can collect selected message notifications. It does not sync private chats or provide full chat history.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android notification access", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (enabled) "Enabled on this device" else "Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Text(if (enabled) "ON" else "OFF", color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (enabled) "MANAGE ACCESS" else "CONNECT NOTIFICATIONS")
                }
                Text(
                    "Access is optional. Android may show notification content from the apps you allow. Kato stores a limited set of previews locally.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "WhatsApp", "Telegram", "Instagram", "Messenger").forEach { choice ->
                FilterChip(
                    selected = filter == choice,
                    onClick = { filter = choice },
                    label = { Text(choice, fontSize = 10.sp) }
                )
            }
        }
        if (entries.isEmpty()) {
            QuietCard(
                title = "Your inbox is quiet",
                detail = if (enabled) "New supported notification previews will appear here. Replies and full conversation sync are not available in this version."
                else "Connect notification access above, then receive a new message in a supported app."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(entries) { entry ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.source.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp)
                                Spacer(Modifier.weight(1f))
                                Text(entry.timeLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            Text(entry.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(entry.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(section: String, onSectionChange: (String) -> Unit) {
    val sections = listOf("Brief", "YouTube", "Learn", "Articles", "Movies", "Podcasts")
    val descriptions = mapOf(
        "Brief" to "A daily briefing with a fixed number of stories and a clear 'caught up' endpoint.",
        "YouTube" to "Follow channels you choose. Uploads will be shown chronologically, without Home, Shorts or suggested-video feeds.",
        "Learn" to "A finite collection for science, history, technology, mathematics and other learning topics.",
        "Articles" to "A quiet reader with adjustable typography, reading progress and offline saves where permitted.",
        "Movies" to "Discover films by time, mood and theme. A free-watch option will appear only after its source is checked.",
        "Podcasts" to "Follow RSS podcast feeds, browse episodes in order and continue listening without autoplay."
    )
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CONTROLLED DISCOVERY", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text("Choose what you came for.", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("No endless feed, trending list or automatic next item.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.take(3).forEach { choice ->
                FilterChip(selected = section == choice, onClick = { onSectionChange(choice) }, label = { Text(choice) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.drop(3).forEach { choice ->
                FilterChip(selected = section == choice, onClick = { onSectionChange(choice) }, label = { Text(choice) })
            }
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(section.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(when (section) {
                    "Brief" -> "Today's briefing"
                    "YouTube" -> "Your channel list"
                    "Learn" -> "Learning collection"
                    "Articles" -> "Reader"
                    "Movies" -> "Movie lounge"
                    else -> "Podcast shelf"
                }, color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(descriptions[section] ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "PHASE 1 · FOUNDATION",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    "This section is a real navigation and design foundation, not a fake content feed. Its source integrations and playback are scheduled for later phases.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
        QuietCard(
            title = "Nothing is being pushed to you",
            detail = "Connect your own sources in a later setup step. Kato will keep lists finite and will not fill empty spaces with algorithmic recommendations."
        )
    }
}

@Composable
private fun LibraryScreen(refresh: Int, onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { KatoDatabase(context) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val categories = listOf("All", "Article", "Video", "Movie", "Music", "Podcast", "Learning")
    val records = remember(refresh, search, filter) { database.listItems(search, filter) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("YOUR COLLECTION", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Text("Saved by you.", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { showAdd = true }) { Text("+ SAVE") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search your library") }
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.take(4).forEach { choice ->
                    FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice, fontSize = 10.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.drop(4).forEach { choice ->
                    FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice, fontSize = 10.sp) })
                }
            }
        }
        if (records.isEmpty()) {
            QuietCard(
                title = "Your Library is ready",
                detail = "Save a link to an article, video, film, podcast or music collection. Items stay on this device."
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
                        onOpen = { openExternal(context, item.url) },
                        onComplete = {
                            database.setCompleted(item.id, !item.completed)
                            onDataChanged()
                        },
                        onDelete = {
                            database.deleteItem(item.id)
                            onDataChanged()
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
                onDataChanged()
            }
        )
    }
}

@Composable
private fun SavedItemCard(
    item: SavedItem,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.type.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                if (item.completed) {
                    Text("COMPLETED", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.note.isNotBlank()) {
                Text(item.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("OPEN SOURCE") }
                TextButton(onClick = onComplete) { Text(if (item.completed) "REOPEN" else "DONE") }
                TextButton(onClick = onDelete) { Text("REMOVE", color = MaterialTheme.colorScheme.error) }
            }
        }
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
        title = { Text("Save to Kato") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it; error = false }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it; error = false }, label = { Text("Original link") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Personal note (optional)") }, minLines = 2)
                Text("CONTENT TYPE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    types.take(3).forEach { choice ->
                        FilterChip(selected = type == choice, onClick = { type = choice }, label = { Text(choice, fontSize = 9.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    types.drop(3).forEach { choice ->
                        FilterChip(selected = type == choice, onClick = { type = choice }, label = { Text(choice, fontSize = 9.sp) })
                    }
                }
                if (error) Text("Enter a title and a valid web link.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanUrl = normalizeUrl(url)
                if (title.isNotBlank() && (cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://"))) {
                    onSave(title.trim(), cleanUrl, type, note.trim())
                } else {
                    error = true
                }
            }) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE) }
    val database = remember(context) { KatoDatabase(context) }
    var recommendationsOff by remember { mutableStateOf(prefs.getBoolean("recommendations_off", true)) }
    var sessionReminder by remember { mutableStateOf(prefs.getBoolean("session_reminder", false)) }
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { SectionHeading("APPEARANCE", "A quiet interface, day or night") }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("System", "Dark", "Light").forEach { option ->
                            FilterChip(selected = themeMode == option, onClick = { onThemeModeChange(option) }, label = { Text(option) })
                        }
                    }
                }
            }
        }
        item { SectionHeading("YOUR EXPERIENCE", "Optional controls, never forced") }
        item {
            SettingSwitchCard(
                title = "Disable recommendations",
                detail = "Keep discovery limited to sources and interests you select.",
                checked = recommendationsOff,
                onCheckedChange = {
                    recommendationsOff = it
                    prefs.edit().putBoolean("recommendations_off", it).apply()
                }
            )
        }
        item {
            SettingSwitchCard(
                title = "Session reminders",
                detail = "A gentle reminder at a natural stopping point. It does not close media.",
                checked = sessionReminder,
                onCheckedChange = {
                    sessionReminder = it
                    prefs.edit().putBoolean("session_reminder", it).apply()
                }
            )
        }
        item { SectionHeading("PRIVACY AND DATA", "Local-first by default") }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stored on this device", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Saved links, notes, theme and session preferences are kept locally. No Kato account or cloud service is required in this build.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TEXT, database.exportJson())
                            }
                            context.startActivity(Intent.createChooser(share, "Export Kato library"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("EXPORT LIBRARY DATA") }
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("CLEAR LOCAL CONTENT")
                    }
                }
            }
        }
        item {
            Text("KATO 0.1 · FOUNDATION BUILD", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local content?") },
            text = { Text("This removes saved library items and captured notification previews from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    database.clearItems()
                    context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE)
                        .edit().remove("kato_notifications").apply()
                    confirmClear = false
                    onDataChanged()
                }) { Text("CLEAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun SettingSwitchCard(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun QuietCard(title: String, detail: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

private fun normalizeUrl(raw: String): String {
    val clean = raw.trim()
    return when {
        clean.startsWith("https://", true) || clean.startsWith("http://", true) -> clean
        clean.contains(".") && !clean.contains(" ") -> "https://" + clean
        else -> clean
    }
}

private fun openExternal(context: Context, rawUrl: String) {
    val safeUrl = normalizeUrl(rawUrl)
    if (!safeUrl.startsWith("https://") && !safeUrl.startsWith("http://")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
    } catch (_: Exception) {
    }
}

private data class KatoNotification(
    val source: String,
    val title: String,
    val message: String,
    val time: Long
) {
    fun timeLabel(): String = try {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
    } catch (_: Exception) {
        ""
    }
}

private object KatoNotifications {
    fun read(context: Context): List<KatoNotification> {
        return try {
            val prefs = context.getSharedPreferences("kato_preferences", Context.MODE_PRIVATE)
            val array = JSONArray(prefs.getString("kato_notifications", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                KatoNotification(
                    source = item.optString("source", "App"),
                    title = item.optString("title", "Notification"),
                    message = item.optString("message", ""),
                    time = item.optLong("time", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
