package com.widgetly.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

internal const val PREFS_NAME = "widgetly_prefs"
internal const val NOTE_KEY = "quick_note"
private val Ink = Color(0xFF111411)
private val Panel = Color(0xFF1B211C)
private val Lime = Color(0xFFC5E879)
private val Muted = Color(0xFFA4AEA1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary=Lime, onPrimary=Ink, background=Ink, surface=Panel)) {
                WidgetlyApp()
            }
        }
    }
}

@Composable
private fun WidgetlyApp() {
    var tab by remember { mutableStateOf("Home") }
    Surface(Modifier.fillMaxSize(), color=Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=18.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Column {
                    Text("WIDGETLY", color=Lime, fontSize=12.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp)
                    Text(when(tab){"Watch"->"Video deck";"Listen"->"Music deck";"Inbox"->"Message center";"Widgets"->"Pocket gadgets";else->"Your pocket dashboard."}, color=Color.White, fontSize=23.sp, fontWeight=FontWeight.SemiBold)
                }
                Text("●  READY", color=Lime, fontSize=10.sp, modifier=Modifier.padding(top=8.dp))
            }
            Box(Modifier.weight(1f)) {
                when(tab) {
                    "Watch" -> MediaPage("YOUTUBE", "Browse and play YouTube videos inside Widgetly. Playback is provided by YouTube's own embedded/mobile experience.", "https://m.youtube.com")
                    "Listen" -> MediaPage("SPOTIFY", "Open Spotify's web player inside Widgetly. Spotify account, playback and Premium restrictions still apply.", "https://open.spotify.com")
                    "Inbox" -> InboxPage()
                    "Widgets" -> WidgetsPage()
                    else -> HomePage(onOpen={tab=it})
                }
            }
            NavigationBar(containerColor=Panel, contentColor=Color.White) {
                listOf("Home","Watch","Listen","Inbox","Widgets").forEach { item ->
                    NavigationBarItem(selected=tab==item, onClick={tab=item}, icon={Text(when(item){"Home"->"⌂";"Watch"->"▣";"Listen"->"♫";"Inbox"->"▤";else->"▦"}, fontSize=18.sp)}, label={Text(item,fontSize=10.sp)}, colors=NavigationBarItemDefaults.colors(selectedIconColor=Lime, selectedTextColor=Lime, indicatorColor=Color(0xFF303A2D), unselectedTextColor=Muted, unselectedIconColor=Muted))
                }
            }
        }
    }
}

@Composable
private fun HomePage(onOpen:(String)->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp).padding(bottom=16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("YOUR CENTERS", color=Muted, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp)
        HubCard("01  /  VIDEO", "YouTube playback inside Widgetly", "Open video deck") { onOpen("Watch") }
        HubCard("02  /  MUSIC", "Spotify web player inside Widgetly", "Open music deck") { onOpen("Listen") }
        HubCard("03  /  SOCIAL", "Collect supported message notifications in one inbox", "Open message center") { onOpen("Inbox") }
        HubCard("04  /  GADGETS", "Quick Note and Retro Clock home-screen widgets", "View widgets") { onOpen("Widgets") }
        Text("Retro hardware, rebuilt as a lightweight Android dashboard.", color=Muted, fontSize=12.sp)
    }
}

@Composable
private fun HubCard(kicker:String, description:String, action:String, onClick:()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Panel), shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(kicker, color=Lime, fontSize=10.sp, fontWeight=FontWeight.Bold, letterSpacing=1.5.sp)
            Text(description, color=Color.White, fontSize=16.sp, fontWeight=FontWeight.Medium)
            Button(onClick=onClick, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF303A2D), contentColor=Lime), shape=RoundedCornerShape(12.dp)) { Text(action) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MediaPage(title:String, description:String, url:String) {
    var address by remember(url) { mutableStateOf(url) }
    var current by remember(url) { mutableStateOf(url) }
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title, color=Lime, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp)
        Text(description, color=Muted, fontSize=12.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value=address, onValueChange={address=it}, modifier=Modifier.weight(1f), singleLine=true, textStyle=LocalTextStyle.current.copy(fontSize=12.sp), label={Text("Video / playlist / track URL")})
            Button(onClick={current=if(address.startsWith("http")) address else "https://www.google.com/search?q="+Uri.encode(address)}, contentPadding=PaddingValues(horizontal=12.dp, vertical=8.dp)) { Text("Go") }
        }
        EmbeddedBrowser(current, Modifier.weight(1f).fillMaxWidth())
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedBrowser(url:String, modifier:Modifier=Modifier) {
    AndroidView(modifier=modifier, factory={ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.mediaPlaybackRequiresUserGesture=true
            settings.javaScriptCanOpenWindowsAutomatically=true
            webChromeClient=WebChromeClient()
            webViewClient=WebViewClient()
            loadUrl(url)
        }
    }, update={view -> if(view.tag != url) { view.tag=url; view.loadUrl(url) } })
}

@Composable
private fun InboxPage() {
    val context= LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val prefs=context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val messages= remember(refresh) {
        try {
            val arr=org.json.JSONArray(prefs.getString("social_notifications","[]"))
            (0 until arr.length()).map { i -> arr.getJSONObject(i) }
        } catch (_:Exception) { emptyList() }
    }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("SOCIAL NOTIFICATION INBOX", color=Lime, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.5.sp)
        Text("Recent message notifications from WhatsApp, Telegram, Instagram and Messenger. This is a notification inbox, not full private-chat API access.", color=Muted, fontSize=12.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}, colors=ButtonDefaults.buttonColors(containerColor=Lime, contentColor=Ink)) { Text("Connect apps") }
            OutlinedButton(onClick={refresh++}) { Text("Refresh") }
        }
        if(messages.isEmpty()) {
            Card(colors=CardDefaults.cardColors(containerColor=Panel), shape=RoundedCornerShape(18.dp)) {
                Text("No message notifications captured yet. Enable Widgetly under Android Settings → Notification access, then receive a new message.", color=Muted, modifier=Modifier.padding(16.dp), fontSize=13.sp)
            }
        } else {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(messages) { item ->
                    Card(colors=CardDefaults.cardColors(containerColor=Panel), shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(item.optString("source")+"  /  "+item.optString("title"), color=Lime, fontWeight=FontWeight.Bold, fontSize=12.sp)
                            Text(item.optString("message"), color=Color.White, fontSize=14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetsPage() {
    val context= LocalContext.current
    val prefs= remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var note by remember { mutableStateOf(prefs.getString(NOTE_KEY,"Make today count.") ?: "") }
    var saved by remember { mutableStateOf(false) }
    val scope= rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("HOME-SCREEN GADGETS", color=Lime, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.5.sp)
        Card(colors=CardDefaults.cardColors(containerColor=Panel), shape=RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("QUICK NOTE  /  2 × 2", color=Lime, fontWeight=FontWeight.Bold)
                OutlinedTextField(value=note, onValueChange={note=it;saved=false}, modifier=Modifier.fillMaxWidth(), label={Text("Your note")})
                Button(onClick={prefs.edit().putString(NOTE_KEY,note.trim()).apply(); scope.launch { QuickNoteWidget().updateAll(context); saved=true }}, colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink)) { Text("Save note widget") }
                if(saved) Text("Updated",color=Lime)
            }
        }
        Text("RETRO CLOCK  /  2 × 1", color=Lime, fontWeight=FontWeight.Bold)
        Text("Add either widget from your Android home screen's Widgets picker.", color=Muted, fontSize=12.sp)
    }
}
