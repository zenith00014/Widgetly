package com.widgetly.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val Ink=Color(0xFF101411)
private val Panel=Color(0xFF1A211C)
private val Panel2=Color(0xFF252F27)
private val Lime=Color(0xFFC8F169)
private val Muted=Color(0xFF9AA69A)
private val Paper=Color(0xFFF2F5EF)
private const val PREFS="nexus_prefs"

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Ink,background=Ink,surface=Panel)) { NexusApp() } }
    }
}
private enum class Tab(val label:String,val icon:String) { HOME("Home","⌂"),DISCOVER("Discover","◉"),INBOX("Inbox","✉"),SAVED("Saved","▣") }

@Composable private fun NexusApp() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    Surface(Modifier.fillMaxSize(),color=Ink) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Column {
                    Text("N E X U S",color=Lime,fontSize=12.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                    Text(when(tab){Tab.HOME->"A better kind of feed";Tab.DISCOVER->"Find something worthwhile";Tab.INBOX->"Your people, in one place";Tab.SAVED->"Keep the good stuff"},color=Paper,fontSize=21.sp,fontWeight=FontWeight.Bold)
                }
                Box(Modifier.size(38.dp).clip(CircleShape).background(Panel2),contentAlignment=Alignment.Center){Text("N",color=Lime,fontWeight=FontWeight.Bold)}
            }
            Box(Modifier.weight(1f)) {
                when(tab) {
                    Tab.HOME->HomeScreen({tab=Tab.DISCOVER},{tab=Tab.INBOX})
                    Tab.DISCOVER->DiscoverScreen()
                    Tab.INBOX->InboxScreen()
                    Tab.SAVED->SavedScreen()
                }
            }
            NavigationBar(containerColor=Panel,contentColor=Paper) {
                Tab.values().forEach { t-> NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Text(t.icon,fontSize=19.sp)},label={Text(t.label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Lime,selectedTextColor=Lime,indicatorColor=Panel2,unselectedIconColor=Muted,unselectedTextColor=Muted)) }
            }
        }
    }
}

@Composable private fun HomeScreen(onDiscover:()->Unit,onInbox:()->Unit) {
    var feed by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { feed=withContext(Dispatchers.IO){FeedRepository.load("All")}; loading=false }
    LazyColumn(contentPadding=PaddingValues(horizontal=17.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors=CardDefaults.cardColors(containerColor=Panel2),shape=RoundedCornerShape(23.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("LESS SCROLLING. MORE LIVING.",color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)
                    Text("A feed with a purpose.",color=Paper,fontSize=25.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp)
                    Text("Thoughtful videos, useful stories and films worth your time.",color=Muted,fontSize=13.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick=onDiscover,colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink)){Text("Explore")}
                        OutlinedButton(onClick=onInbox){Text("Inbox")}
                    }
                }
            }
        }
        item { Column { Text("A little inspiration",color=Paper,fontSize=17.sp,fontWeight=FontWeight.Bold);Text("Fresh from selected sources",color=Muted,fontSize=11.sp) } }
        if(loading) item { LoadingCard() }
        items(feed.take(6)) { FeedCard(it) }
        item { Text("Your attention is valuable. Nexus keeps discovery intentional.",color=Muted,fontSize=12.sp) }
    }
}

@Composable private fun DiscoverScreen() {
    val categories=listOf("All","Videos","News","Movies")
    var selected by remember { mutableStateOf("All") }
    var feed by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    fun refresh(category:String) {
        selected=category; loading=true; error=null
        scope.launch {
            try { feed=withContext(Dispatchers.IO){FeedRepository.load(category)}; if(feed.isEmpty()) error="No items returned. Check your connection or refresh." }
            catch(_:Exception) { error="Couldn't load this feed. Check your connection and try again." }
            loading=false
        }
    }
    LaunchedEffect(Unit) { refresh("All") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            categories.forEach { c->FilterChip(selected==c,onClick={if(selected!=c)refresh(c)},label={Text(c)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Ink,containerColor=Panel,labelColor=Muted)) }
            OutlinedButton(onClick={refresh(selected)},contentPadding=PaddingValues(horizontal=12.dp,vertical=5.dp)){Text("Refresh")}
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=17.dp,vertical=7.dp),color=Lime,trackColor=Panel2)
        error?.let { Text(it,color=Muted,fontSize=12.sp,modifier=Modifier.padding(horizontal=18.dp,vertical=6.dp)) }
        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(feed.isEmpty()&&loading) item { LoadingCard() }
            items(feed) { FeedCard(it) }
        }
    }
}

@Composable private fun FeedCard(item:FeedItem) {
    val context=androidx.compose.ui.platform.LocalContext.current
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
        Column {
            if(!item.image.isNullOrBlank()) AsyncImage(model=item.image,contentDescription=item.title,modifier=Modifier.fillMaxWidth().height(174.dp).background(Panel2),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(item.category.uppercase()+"  ·  "+item.source,color=Lime,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Text(item.title,color=Paper,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(item.summary.isNotBlank()) Text(item.summary,color=Muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
                    Text("OPEN SOURCE  ↗",color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(item.link))) } }.padding(vertical=6.dp))
                    Text("＋ SAVE",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable { saveItem(context,item.title,item.link) }.padding(6.dp))
                }
            }
        }
    }
}
private fun saveItem(context:Context,title:String,link:String) {
    val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    val a=runCatching{JSONArray(prefs.getString("saved_items","[]"))}.getOrDefault(JSONArray())
    if((0 until a.length()).none{a.optJSONObject(it)?.optString("link")==link}) a.put(org.json.JSONObject().put("title",title).put("link",link))
    prefs.edit().putString("saved_items",a.toString()).apply()
}
@Composable private fun LoadingCard() { Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)){Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(color=Lime,modifier=Modifier.size(21.dp),strokeWidth=2.dp);Text("Gathering useful things…",color=Muted,fontSize=12.sp)}} }

@Composable private fun InboxScreen() {
    val context=androidx.compose.ui.platform.LocalContext.current
    var showContacts by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<ContactEntry>>(emptyList()) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED) }
    var refresh by remember { mutableIntStateOf(0) }
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok->granted=ok;if(ok)contacts=readContacts(context) }
    val messages=remember(refresh) {
        val a=runCatching{JSONArray(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("social_notifications","[]"))}.getOrDefault(JSONArray())
        (0 until a.length()).mapNotNull{a.optJSONObject(it)}
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            FilterChip(!showContacts,{showContacts=false},label={Text("Inbox")})
            FilterChip(showContacts,{showContacts=true;if(granted)contacts=readContacts(context)else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)},label={Text("Contacts")})
            Spacer(Modifier.weight(1f))
            TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));refresh++}){Text("Connect apps",color=Lime)}
            TextButton(onClick={refresh++}){Text("↻",color=Lime)}
        }
        if(showContacts) {
            if(!granted) Text("Allow access to your phone contacts to see people here.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(18.dp))
            LazyColumn(contentPadding=PaddingValues(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(contacts) { c->
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(15.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(c.name,color=Paper,fontWeight=FontWeight.SemiBold);Text(c.phone,color=Muted,fontSize=12.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(c.phone))))}){Text("SMS",color=Lime)}
                                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+c.phone.filter{it.isDigit()})))}){Text("WhatsApp",color=Lime)}
                                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(c.phone))))}){Text("Call",color=Lime)}
                            }
                        }
                    }
                }
            }
        } else {
            Text("Notification previews and supported quick replies. Full private chat history stays with each messaging app.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(horizontal=18.dp,vertical=7.dp))
            if(messages.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Your inbox is quiet.",color=Paper,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text("Enable notification access. New messages from supported apps will appear here.",color=Muted,fontSize=12.sp)
                    Button(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Ink)){Text("Enable notification access")}
                }
            } else LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(messages) { msg->
                    val key=msg.optString("key")
                    var reply by remember(key){mutableStateOf("")}
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(15.dp)) {
                        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(msg.optString("source")+"  ·  "+msg.optString("title"),color=Lime,fontSize=12.sp,fontWeight=FontWeight.Bold)
                            Text(msg.optString("message"),color=Paper,fontSize=14.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={context.packageManager.getLaunchIntentForPackage(msg.optString("package"))?.let{context.startActivity(it)}}){Text("Open app",color=Lime)}
                                if(NotificationReplyStore.has(key)) TextButton(onClick={NotificationReplyStore.reply(context,key,reply);reply=""}){Text("Send reply",color=Lime)}
                            }
                            if(NotificationReplyStore.has(key)) OutlinedTextField(value=reply,onValueChange={reply=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Quick reply…")})
                        }
                    }
                }
            }
        }
    }
}
private data class ContactEntry(val name:String,val phone:String)
private fun readContacts(context:Context):List<ContactEntry> {
    val out=mutableListOf<ContactEntry>()
    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")?.use { c->
        while(c.moveToNext()&&out.size<200) { val name=c.getString(0)?:continue;val phone=c.getString(1)?:continue;out.add(ContactEntry(name,phone)) }
    }
    return out.distinctBy{it.phone}
}
@Composable private fun SavedScreen() {
    val context=androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf(loadSaved(context)) }
    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("YOUR COLLECTION",color=Lime,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
        if(saved.isEmpty()) Text("Save videos, articles and films as you discover them. They'll stay here for later.",color=Muted,fontSize=13.sp)
        saved.forEach { entry->
            Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth().clickable{runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(entry.second)))}}) {
                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically) { Text(entry.first,color=Paper,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis);Text("↗",color=Lime) }
            }
        }
    }
}
private fun loadSaved(context:Context):List<Pair<String,String>> {
    val a=runCatching{JSONArray(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("saved_items","[]"))}.getOrDefault(JSONArray())
    return (0 until a.length()).mapNotNull{a.optJSONObject(it)?.let{j->j.optString("title") to j.optString("link")}}
}
