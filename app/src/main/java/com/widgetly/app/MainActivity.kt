package com.widgetly.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
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
import coil.compose.AsyncImage
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

internal const val PREFS_NAME = "widgetly_prefs"
internal const val NOTE_KEY = "nexus_personal_note"
private val Ink = Color(0xFF0E0F0C)
private val Panel = Color(0xFF1A1B17)
private val Paper = Color(0xFFECE9DF)
private val Muted = Color(0xFF8B8D82)
private val Lime = Color(0xFFC8F23A)
private val Line = Color(0xFF3A3C34)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary=Lime,onPrimary=Ink,background=Ink,surface=Panel)) { NexusApp() } }
    }
}

@Composable
private fun NexusApp() {
    val context = LocalContext.current
    val musicPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(musicPlayer) { onDispose { musicPlayer.release() } }
    var tab by remember { mutableStateOf("HOME") }
    var opened by remember { mutableStateOf<NexusItem?>(null) }
    Surface(Modifier.fillMaxSize(), color=Ink) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=14.dp)) {
                Text("N E X U S", color=Lime, fontSize=12.sp, fontWeight=FontWeight.Black)
                Text(if (opened != null) "IN-APP VIEWER" else when(tab) {
                    "DISCOVER" -> "A finite discovery session"
                    "INBOX" -> "Your message center"
                    "LIBRARY" -> "Your saved collection"
                    "MUSIC" -> "Your local music shelf"
                    else -> "Less scrolling. More signal."
                }, color=Paper, fontSize=20.sp, fontWeight=FontWeight.Bold)
            }
            Box(Modifier.weight(1f)) {
                if (opened != null) NexusViewer(opened!!, { opened=null }) else when(tab) {
                    "DISCOVER" -> DiscoverPage { opened=it }
                    "INBOX" -> InboxPage()
                    "LIBRARY" -> LibraryPage { opened=it }
                    "MUSIC" -> NexusMusicPage(musicPlayer)
                    else -> HomePage({tab="DISCOVER"}, {tab="INBOX"}, {tab="LIBRARY"}, {opened=it})
                }
            }
            if (opened == null) NavigationBar(containerColor=Panel) {
                listOf("HOME","DISCOVER","INBOX","LIBRARY","MUSIC").forEach { item ->
                    NavigationBarItem(selected=tab==item,onClick={tab=item},
                        icon={Text(when(item){"HOME"->"⌂";"DISCOVER"->"▣";"INBOX"->"▤";"LIBRARY"->"▦";else->"♫"},fontSize=19.sp)},
                        label={Text(item,fontSize=9.sp,letterSpacing=1.sp)},
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=Lime,selectedTextColor=Lime,indicatorColor=Color(0xFF30352A),unselectedIconColor=Muted,unselectedTextColor=Muted))
                }
            }
        }
    }
}

@Composable
private fun HomePage(onDiscover:()->Unit,onInbox:()->Unit,onLibrary:()->Unit,onOpen:(NexusItem)->Unit) {
    var videos by remember { mutableStateOf<List<NexusItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { videos=runCatching { NexusContentRepository.videos().take(3) }.getOrDefault(emptyList()); loading=false }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(top=8.dp,bottom=20.dp)) {
        item { Slab {
            Text("TODAY'S SIGNAL",color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
            Text("A small, intentional window into what matters.",color=Paper,fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text("No infinite feed. No autoplay. You choose when to stop.",color=Muted,fontSize=12.sp)
            Button(onClick=onDiscover,colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink),shape=RectangleShape) { Text("OPEN DISCOVER  →") }
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            QuickTile("INBOX","Messages & replies",Modifier.weight(1f),onInbox)
            QuickTile("LIBRARY","Saved for later",Modifier.weight(1f),onLibrary)
        } }
        item { Text("MUSIC  /  YOUR LOCAL AUDIO",color=Muted,fontSize=9.sp,modifier=Modifier.clickable { onLibrary() }) }
        item { SectionLabel("A FEW GOOD VIDEOS","MAX 3") }
        if(loading) item { Text("Loading selected channels…",color=Muted,fontSize=12.sp) }
        if(!loading && videos.isEmpty()) item { Text("Video shelf unavailable. Open Discover to retry.",color=Muted,fontSize=12.sp) }
        items(videos,key={it.id}) { ContentCard(it,{onOpen(it)}) }
        item { Text("CHANNEL RSS  /  EDITORIAL SOURCES  /  PUBLIC CATALOGS",color=Muted,fontSize=9.sp) }
    }
}

@Composable
private fun QuickTile(title:String,subtitle:String,modifier:Modifier,onClick:()->Unit) {
    Slab(modifier.clickable(onClick=onClick)) {
        Text(title,color=Lime,fontWeight=FontWeight.Bold,fontSize=11.sp)
        Text(subtitle,color=Paper,fontSize=12.sp)
        Text("OPEN  ↗",color=Muted,fontSize=9.sp)
    }
}

@Composable
private fun DiscoverPage(onOpen:(NexusItem)->Unit) {
    val context=LocalContext.current
    var sessionLimit by remember { mutableIntStateOf(context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).getInt("session_limit",12)) }
    var dailyRefresh by remember { mutableIntStateOf(0) }
    val dailyUsed = remember(dailyRefresh) { NexusUsage.used(context) }
    val dailyBudget = NexusUsage.budget(context)
    val dailySeconds = remember(dailyRefresh) { NexusUsage.seconds(context) }
    val dailyTimeBudget = NexusUsage.timeBudgetSeconds(context)
    val lifecycleOwner = LocalLifecycleOwner.current
    var appVisible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) appVisible = true
            if (event == Lifecycle.Event.ON_STOP) appVisible = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(appVisible,dailySeconds,dailyUsed) {
        if (appVisible && dailySeconds < dailyTimeBudget && dailyUsed < dailyBudget) {
            delay(1000); NexusUsage.addDiscoverSecond(context); dailyRefresh++
        }
    }
    var kind by remember { mutableStateOf(ContentKind.VIDEO) }
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<List<NexusItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val openItem: (NexusItem) -> Unit = { item -> if (NexusUsage.recordOpen(context)) { dailyRefresh++; onOpen(item) } else dailyRefresh++ }
    LaunchedEffect(kind,refresh) {
        loading=true; error=null
        val result=runCatching { when(kind) {
            ContentKind.VIDEO -> NexusContentRepository.videos()
            ContentKind.NEWS -> NexusContentRepository.news()
            ContentKind.MOVIE -> NexusContentRepository.movies()
        } }
        data=result.getOrDefault(emptyList()); error=result.exceptionOrNull()?.localizedMessage; loading=false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
            listOf(ContentKind.VIDEO,ContentKind.NEWS,ContentKind.MOVIE).forEach { option ->
                FilterChip(selected=kind==option,onClick={kind=option},label={Text(when(option){ContentKind.VIDEO->"VIDEOS";ContentKind.NEWS->"NEWS";else->"MOVIES"},fontSize=10.sp)},
                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,labelColor=Paper))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick={refresh++}) { Text("REFRESH",color=Lime,fontSize=10.sp) }
        }
        Slab(Modifier.padding(horizontal=14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("SESSION LIMIT",color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text("$sessionLimit ITEMS",color=Paper,fontSize=10.sp)
            }
            Slider(value=sessionLimit.toFloat(),onValueChange={sessionLimit=it.toInt().coerceIn(5,30)},valueRange=5f..30f,steps=4)
            Text("A finite set. Autoplay stays off.",color=Muted,fontSize=10.sp)
            Text("TODAY  /  $dailyUsed OF $dailyBudget ITEMS · ${dailySeconds/60} OF ${dailyTimeBudget/60} MIN",color=Muted,fontSize=9.sp)
            TextButton(onClick={context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).edit().putInt("session_limit",sessionLimit).apply();refresh++}) { Text("APPLY SESSION LIMIT",color=Lime,fontSize=9.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("FINITE SESSION  /  ${minOf(data.size,sessionLimit)} ITEMS",color=Muted,fontSize=9.sp)
            Text("AUTOPLAY OFF",color=Lime,fontSize=9.sp)
        }
        when {
            dailyUsed >= dailyBudget || dailySeconds >= dailyTimeBudget -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Slab {
                Text("TODAY’S STOPPING POINT",color=Lime,fontWeight=FontWeight.Bold,fontSize=13.sp)
                Text("You reached your daily Discover limit. Come back tomorrow, or adjust your limits in Library → Settings.",color=Muted,fontSize=12.sp)
            } }
            loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator(color=Lime) }
            data.isEmpty() -> Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("NO ITEMS AVAILABLE",color=Lime,fontWeight=FontWeight.Bold)
                Text(error ?: "This source returned no items. Check your connection and retry.",color=Muted,fontSize=12.sp)
                OutlinedButton(onClick={refresh++}) { Text("TRY AGAIN") }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(vertical=12.dp)) {
                items(data.take(sessionLimit),key={it.id}) { ContentCard(it,{openItem(it)}) }
                item { Slab {
                    Text("END OF SESSION",color=Lime,fontWeight=FontWeight.Bold,fontSize=12.sp)
                    Text("You've reached the end of this set. Save an item or close Discover.",color=Muted,fontSize=12.sp)
                } }
            }
        }
    }
}

@Composable
private fun ContentCard(item:NexusItem,onClick:()->Unit,onChanged:()->Unit={}) {
    val context = LocalContext.current
    var saved by remember(item.id) { mutableStateOf(NexusLocalStore.isSaved(context, item.id)) }
    Column {
    Row(Modifier.fillMaxWidth().background(Panel).clickable(onClick=onClick).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(!item.image.isNullOrBlank()) AsyncImage(model=item.image,contentDescription="Thumbnail for ${item.title}",modifier=Modifier.width(112.dp).height(82.dp))
        else Box(Modifier.width(112.dp).height(82.dp).background(Color(0xFF30352A)),contentAlignment=Alignment.Center) { Text("N X",color=Lime,fontWeight=FontWeight.Black) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(item.source.uppercase(),color=Lime,fontSize=9.sp,fontWeight=FontWeight.Bold)
            Text(item.title,color=Paper,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=3)
            Text(item.description.ifBlank { item.published },color=Muted,fontSize=10.sp,maxLines=2)
            Text(when(item.kind){ContentKind.VIDEO->"▶ PLAY INSIDE NEXUS";ContentKind.NEWS->"READ INSIDE NEXUS";ContentKind.MOVIE->"▶ WATCH INSIDE NEXUS"},color=Lime,fontSize=9.sp,fontWeight=FontWeight.Bold)
            TextButton(onClick={ saved = if(saved) { NexusLocalStore.remove(context,item.id); false } else { NexusLocalStore.save(context,item); true }; onChanged() }) { Text(if(saved) "SAVED ✓" else "+ SAVE",color=if(saved) Lime else Muted,fontSize=9.sp) }
        }
    }
    HorizontalDivider(color=Line,thickness=1.dp)
    }
}

@Composable
private fun SectionLabel(title:String,meta:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title,color=Paper,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
        Text(meta,color=Muted,fontSize=9.sp)
    }
}

@Composable
private fun Slab(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().background(Panel).padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
}

@Composable
private fun InboxPage() {
    val context=LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<JSONObject?>(null) }
    var reply by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf("ALL") }
    val prefs=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE)
    val messages=remember(refresh) {
        try { val arr=JSONArray(prefs.getString("social_notifications","[]")); (0 until arr.length()).map { arr.getJSONObject(it) } }
        catch (_:Exception) { emptyList() }
    }
    val visibleMessages = messages.filter { msg ->
        val matchesSource = sourceFilter == "ALL" || msg.optString("source").equals(sourceFilter, true)
        val query = search.trim()
        matchesSource && (query.isBlank() || listOf("source", "title", "message").any { msg.optString(it).contains(query, true) })
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=8.dp)) {
        Slab {
            Text("HYBRID MESSAGE INBOX",color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold)
            Text("PREVIEW = notification history. LIVE = only with an authorized integration. Nexus does not silently read private chats.",color=Muted,fontSize=11.sp)
            OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search previews")})
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf("ALL","WHATSAPP","TELEGRAM","INSTAGRAM","MESSENGER").forEach { value ->
                    FilterChip(selected=sourceFilter==value,onClick={sourceFilter=value},label={Text(value,fontSize=8.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,labelColor=Paper))
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink),shape=RectangleShape) { Text("ENABLE INBOX",fontSize=10.sp) }
                OutlinedButton(onClick={refresh++},shape=RectangleShape) { Text("REFRESH",fontSize=10.sp) }
                TextButton(onClick={prefs.edit().remove("social_notifications").apply();refresh++}) { Text("CLEAR HISTORY",color=Muted,fontSize=9.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if(messages.isEmpty()) Slab {
            Text("NO MESSAGE PREVIEWS YET",color=Paper,fontWeight=FontWeight.Bold)
            Text("Enable Nexus under Android Notification Access, then receive a message in a supported app.",color=Muted,fontSize=12.sp)
        } else if (visibleMessages.isEmpty()) Slab { Text("NO MATCHING PREVIEWS",color=Paper,fontWeight=FontWeight.Bold); Text("Try another search or source filter.",color=Muted,fontSize=12.sp) }
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=18.dp)) {
            items(visibleMessages,key={it.optString("key")+it.optLong("time")}) { msg ->
                val key=msg.optString("key")
                val canReply=msg.optBoolean("canReply") && NexusNotificationBridge.canReply(key)
                Slab {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(msg.optString("source").uppercase(),color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold)
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.optLong("time"))),color=Muted,fontSize=9.sp)
                    }
                    Text(msg.optString("title"),color=Paper,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                    Text(msg.optString("message"),color=Muted,fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(canReply) OutlinedButton(onClick={target=msg;reply=""},shape=RectangleShape) { Text("QUICK REPLY",fontSize=9.sp) }
                        TextButton(onClick={NexusNotificationBridge.openOriginal(context,msg.optString("package"))}) { Text("OPEN APP ↗",color=Lime,fontSize=9.sp) }
                        TextButton(onClick={
                            val remaining=JSONArray()
                            for (i in 0 until JSONArray(prefs.getString("social_notifications","[]")).length()) {
                                val entry=JSONArray(prefs.getString("social_notifications","[]")).getJSONObject(i)
                                if (!(entry.optString("key")==key && entry.optLong("time")==msg.optLong("time"))) remaining.put(entry)
                            }
                            prefs.edit().putString("social_notifications",remaining.toString()).apply();refresh++
                        }) { Text("REMOVE",color=Muted,fontSize=9.sp) }
                    }
                }
            }
        }
    }
    if(target!=null) AlertDialog(
        onDismissRequest={target=null},
        title={Text("Quick reply · ${target?.optString("source")}")},
        text={OutlinedTextField(value=reply,onValueChange={reply=it},label={Text("Message")})},
        confirmButton={TextButton(onClick={
            val sent=NexusNotificationBridge.reply(target?.optString("key").orEmpty(),reply)
            if(!sent) NexusNotificationBridge.openOriginal(context,target?.optString("package").orEmpty())
            target=null
        },enabled=reply.isNotBlank()) { Text("SEND") }},
        dismissButton={TextButton(onClick={target=null}) { Text("CANCEL") }},
        containerColor=Panel,titleContentColor=Paper,textContentColor=Paper
    )
}

@Composable
private fun LibraryPage(onOpen:(NexusItem)->Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE) }
    var note by remember { mutableStateOf(prefs.getString(NOTE_KEY,"Make today count.") ?: "") }
    var noteSaved by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var section by remember { mutableStateOf("SAVED") }
    val savedItems = remember(refresh) { NexusLocalStore.all(context) }
    var filter by remember { mutableStateOf("ALL") }
    val visibleItems = remember(savedItems,filter) { savedItems.filter { filter=="ALL" || it.kind.name==filter } }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Slab {
            Text("PERSONAL HUB",color=Lime,fontWeight=FontWeight.Bold,fontSize=11.sp)
            Text("Your saved media, notes and contact shortcuts.",color=Muted,fontSize=12.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=section=="SAVED",onClick={section="SAVED"},label={Text("SAVED + NOTES")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,labelColor=Paper))
                FilterChip(selected=section=="CONTACTS",onClick={section="CONTACTS"},label={Text("CONTACTS")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,labelColor=Paper))
                FilterChip(selected=section=="SETTINGS",onClick={section="SETTINGS"},label={Text("SETTINGS")},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,labelColor=Paper))
            }
        }
        if(section=="CONTACTS") {
            NexusContactsPanel()
        } else if(section=="SETTINGS") {
            NexusSettingsPanel()
        } else {
            Slab {
                Text("PERSONAL NOTE",color=Lime,fontWeight=FontWeight.Bold,fontSize=11.sp)
                OutlinedTextField(value=note,onValueChange={note=it;noteSaved=false},modifier=Modifier.fillMaxWidth(),label={Text("Write a note")})
                Button(onClick={prefs.edit().putString(NOTE_KEY,note.trim()).apply();noteSaved=true},colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink),shape=RectangleShape) { Text("SAVE NOTE") }
                if(noteSaved) Text("Saved on this device.",color=Lime,fontSize=11.sp)
            }
            Slab {
                Text("SAVED ITEMS · ${savedItems.size}",color=Paper,fontWeight=FontWeight.Bold,fontSize=11.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf("ALL","VIDEO","NEWS","MOVIE").forEach { value -> FilterChip(selected=filter==value,onClick={filter=value},label={Text(value,fontSize=9.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,labelColor=Paper)) }
                }
            }
            if(visibleItems.isEmpty()) Slab { Text(if(savedItems.isEmpty()) "NOTHING SAVED YET" else "NO ${filter} ITEMS",color=Paper,fontWeight=FontWeight.Bold); Text("Use + SAVE on any video, article or movie card.",color=Muted,fontSize=12.sp) }
            else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=18.dp)) {
                items(visibleItems,key={it.id}) { item -> ContentCard(item,{onOpen(item)},{refresh++}) }
            }
        }
    }
}

@Composable
private fun NexusViewer(item:NexusItem,onBack:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var offlineVersion by remember(item.id) { mutableIntStateOf(0) }
    var savingOffline by remember { mutableStateOf(false) }
    var offlineStatus by remember { mutableStateOf<String?>(null) }
    val hasOffline = remember(item.id,offlineVersion) { item.kind==ContentKind.NEWS && NexusOfflineCache.has(context,item.id) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("← BACK",color=Lime,fontWeight=FontWeight.Bold,modifier=Modifier.clickable(onClick=onBack).padding(6.dp))
            Spacer(Modifier.width(10.dp)); Text(item.source.uppercase(),color=Muted,fontSize=10.sp)
        }
        Text(item.title,color=Paper,fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(14.dp))
        if(item.kind==ContentKind.NEWS) {
            Text(item.description,color=Muted,fontSize=12.sp,modifier=Modifier.padding(horizontal=14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={
                    scope.launch {
                        savingOffline=true
                        val ok=NexusOfflineCache.save(context,item)
                        savingOffline=false
                        offlineVersion++
                        offlineStatus=if(ok) "Saved as a text-only offline article." else "Could not extract readable text from this publisher."
                    }
                },enabled=!savingOffline) { Text(if(savingOffline) "SAVING…" else if(hasOffline) "UPDATE OFFLINE COPY" else "SAVE OFFLINE",color=Lime,fontSize=10.sp) }
                if(hasOffline) TextButton(onClick={NexusOfflineCache.remove(context,item.id);offlineVersion++;offlineStatus="Offline copy removed."}) { Text("REMOVE COPY",color=Muted,fontSize=9.sp) }
                if(offlineStatus!=null) Text(offlineStatus!!,color=Muted,fontSize=9.sp,maxLines=2)
            }
        }
        EmbeddedMedia(item,Modifier.weight(1f).fillMaxWidth(),offlineVersion)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedMedia(item:NexusItem,modifier:Modifier=Modifier,refreshKey:Int=0) {
    val context=LocalContext.current
    val target=when(item.kind) {
        ContentKind.VIDEO -> {
            val id=Regex("[?&]v=([^&]+)").find(item.url)?.groupValues?.get(1) ?: Regex("youtu\\.be/([^?&]+)").find(item.url)?.groupValues?.get(1)
            if(id!=null) "https://www.youtube-nocookie.com/embed/$id?playsinline=1&rel=0" else item.url
        }
        ContentKind.MOVIE -> "https://archive.org/embed/${item.id.removePrefix("ia:")}"
        ContentKind.NEWS -> item.url
    }
    val offlineHtml=if(item.kind==ContentKind.NEWS) NexusOfflineCache.read(context,item.id) else null
    val targetKey=offlineHtml?.hashCode() ?: target.hashCode()+refreshKey
    AndroidView(modifier=modifier,factory={ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled=offlineHtml==null
            settings.domStorageEnabled=true
            settings.mediaPlaybackRequiresUserGesture=true; settings.javaScriptCanOpenWindowsAutomatically=false
            if(item.kind==ContentKind.NEWS) settings.cacheMode=android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
            webChromeClient=WebChromeClient(); webViewClient=WebViewClient()
            if(offlineHtml!=null) loadDataWithBaseURL(item.url,offlineHtml,"text/html","UTF-8",null) else loadUrl(target)
            tag=targetKey
        }
    },update={view -> if(view.tag!=targetKey) {
        view.tag=targetKey
        if(offlineHtml!=null) view.loadDataWithBaseURL(item.url,offlineHtml,"text/html","UTF-8",null) else view.loadUrl(target)
    }})
}
