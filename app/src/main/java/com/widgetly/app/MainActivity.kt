package com.widgetly.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bgDark=Color(0xFF14101F)
private val panelDark=Color(0xFF211A30)
private val raised=Color(0xFF30243F)
private val magenta=Color(0xFFFF3D81)
private val mint=Color(0xFF7CF5D0)
private val paper=Color(0xFFF6F1FB)
private val lilac=Color(0xFFE9E7F1)
private val mutedDark=Color(0xFFBDB3CA)

private data class Pick(val id:String,val title:String,val creator:String,val music:Boolean,val colors:List<Color>,val reason:String,val localPath:String?=null)
private data class SubscribedChannel(val id:String,val title:String,val subscriberCount:String)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{RushApp(getSharedPreferences("rush_prefs",Context.MODE_PRIVATE))}}}
@Composable private fun RushApp(prefs:android.content.SharedPreferences){
 var tab by remember{mutableStateOf("Home")};var light by remember{mutableStateOf(prefs.getBoolean("light",false))}
 var active by remember{mutableStateOf<Pick?>(null)}
 var saved by remember{mutableStateOf(prefs.getStringSet("saved",emptySet())?.toSet()?:emptySet())}
 var history by remember{mutableStateOf(loadHistory(prefs))}}
 var metadata by remember{mutableStateOf(loadPickMetadata(prefs))}
 val bg=if(light)lilac else bgDark;val surface=if(light)Color.White else panelDark
 val ink=if(light)Color(0xFF241B32) else paper;val muted=if(light)Color(0xFF70677D) else mutedDark
 MaterialTheme(colorScheme=if(light)lightColorScheme(primary=magenta,background=bg,surface=surface,onSurface=ink)else darkColorScheme(primary=magenta,background=bg,surface=surface,onSurface=ink)){
  Surface(Modifier.fillMaxSize(),color=bg){Column(Modifier.fillMaxSize()){
   if(active!=null)RushPlayer(active!!,light){active=null}
   else{
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Column{Text("RUSH",color=if(light)magenta else mint,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp);Text(when(tab){"Discover"->"Find your next favourite";"Downloads"->"Offline library";"Subscriptions"->"Latest uploads";"Library"->"Your collection";else->"Your daily signal"},color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold)}
     TextButton(onClick={light=!light;prefs.edit().putBoolean("light",light).apply()}){Text(if(light)"Dark theme" else "Light theme",color=muted)}
    }
    Box(Modifier.weight(1f)){
     when(tab){
      "Discover"->LiveDiscover(ink,muted,surface,saved,{id->saved=toggle(saved,id);prefs.edit().putStringSet("saved",saved).apply()},{result->val pick=Pick(result.videoId,result.title,result.creator,result.isMusic,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)), "Live YouTube result");persistPickMetadata(prefs,pick);metadata=metadata+(pick.id to pick);active=pick;history=listOf(pick.id)+history.filterNot{x->x==pick.id};persistHistory(prefs,history)})
      "Downloads"->DownloadsScreen(ink,muted,surface,{media->active=Pick(media.id,media.title,media.creator,media.isAudio,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Downloaded for offline playback",media.file.absolutePath)})
      "Subscriptions"->SubscriptionsScreen(ink,muted,surface,{result->val pick=Pick(result.videoId,result.title,result.creator,result.isMusic,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Latest upload");persistPickMetadata(prefs,pick);metadata=metadata+(pick.id to pick);active=pick;history=listOf(pick.id)+history.filterNot{x->x==pick.id};prefs.edit().putStringSet("history",history.toSet()).apply()})
      "Library"->Library(saved,history,metadata,prefs,ink,muted,surface,{id->metadata[id]?.let{active=it}},{id->saved=saved-id;prefs.edit().putStringSet("saved",saved).apply()})
      else->Home(ink,muted,surface,prefs,saved,metadata,history,{pick->persistPickMetadata(prefs,pick);metadata=metadata+(pick.id to pick);active=pick;history=listOf(pick.id)+history.filterNot{x->x==pick.id};prefs.edit().putStringSet("history",history.toSet()).apply()},{id->saved=toggle(saved,id);prefs.edit().putStringSet("saved",saved).apply()},{tab="Discover"})
     }
    }
    history.firstOrNull()?.let{metadata[it]}?.let{recent->Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp).clip(RoundedCornerShape(15.dp)).background(surface).clickable{active=recent}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Cover(recent,Modifier.size(40.dp));Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text("PICK UP WHERE YOU LEFT OFF",fontSize=9.sp,color=muted);Text(recent.title,fontSize=13.sp,color=ink)};Text("▶",color=magenta)}}
    NavigationBar(containerColor=surface){listOf("Home","Discover","Subscriptions","Downloads","Library").forEach{dest->NavigationBarItem(selected=tab==dest,onClick={tab=dest},icon={Text(when(dest){"Home"->"⌂";"Discover"->"⌕";"Downloads"->"⇩";"Subscriptions"->"◉";else->"▣"},fontSize=19.sp)},label={Text(dest,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=magenta,selectedTextColor=magenta,indicatorColor=if(light)Color(0xFFF8DCE8)else raised,unselectedIconColor=muted,unselectedTextColor=muted))}}
   }
  }}
 }
}
private fun toggle(s:Set<String>,id:String)=if(id in s)s-id else s+id
private fun loadHistory(prefs:android.content.SharedPreferences):List<String>{
 val ordered=runCatching{org.json.JSONArray(prefs.getString("history_order","[]")?:"[]")}.getOrDefault(org.json.JSONArray())
 if(ordered.length()>0)return (0 until ordered.length()).mapNotNull{ordered.optString(it).takeIf(String::isNotBlank)}
 return prefs.getStringSet("history",emptySet())?.toList().orEmpty()
}
private fun persistHistory(prefs:android.content.SharedPreferences,history:List<String>){
 val ordered=org.json.JSONArray();history.distinct().take(200).forEach(ordered::put)
 prefs.edit().putString("history_order",ordered.toString()).putStringSet("history",history.toSet()).apply()
}
private fun loadPickMetadata(prefs:android.content.SharedPreferences):Map<String,Pick>{
 val root=runCatching{org.json.JSONObject(prefs.getString("item_metadata","{}")?:"{}")}.getOrDefault(org.json.JSONObject())
 val result=linkedMapOf<String,Pick>()
 val keys=root.keys()
 while(keys.hasNext()){
  val id=keys.next()
  val item=root.optJSONObject(id)?:continue
  result[id]=Pick(id,item.optString("title"),item.optString("creator"),item.optBoolean("music"),listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)), "Saved from YouTube")
 }
 return result
}
private fun loadSubscribedChannels(prefs:android.content.SharedPreferences):List<SubscribedChannel>{
 val ids=prefs.getStringSet("subscriptions",emptySet())?.toList().orEmpty()
 val root=runCatching{org.json.JSONObject(prefs.getString("channel_metadata","{}")?:"{}")}.getOrDefault(org.json.JSONObject())
 return ids.mapNotNull{id->val item=root.optJSONObject(id)?:return@mapNotNull null;SubscribedChannel(id,item.optString("title","YouTube channel"),item.optString("subscribers",""))}
}
private fun persistChannelMetadata(prefs:android.content.SharedPreferences,channel:YouTubeSearchResult){
 val root=runCatching{org.json.JSONObject(prefs.getString("channel_metadata","{}")?:"{}")}.getOrDefault(org.json.JSONObject())
 root.put(channel.channelId,org.json.JSONObject().put("title",channel.title).put("subscribers",channel.creator))
 prefs.edit().putString("channel_metadata",root.toString()).apply()
}
private fun persistPickMetadata(prefs:android.content.SharedPreferences,pick:Pick){
 val root=runCatching{org.json.JSONObject(prefs.getString("item_metadata","{}")?:"{}")}.getOrDefault(org.json.JSONObject())
 root.put(pick.id,org.json.JSONObject().put("title",pick.title).put("creator",pick.creator).put("music",pick.music))
 prefs.edit().putString("item_metadata",root.toString()).apply()
}

@Composable private fun Home(
 ink:Color,
 muted:Color,
 surface:Color,
 prefs:android.content.SharedPreferences,
 saved:Set<String>,
 metadata:Map<String,Pick>,
 history:List<String>,
 play:(Pick)->Unit,
 save:(String)->Unit,
 discover:()->Unit
){
 var uploads by remember{mutableStateOf<List<YouTubeSearchResult>>(emptyList())}
 var channels by remember{mutableStateOf(loadSubscribedChannels(prefs))}
 var status by remember{mutableStateOf("Latest uploads from your subscriptions.")}
 var refresh by remember{mutableIntStateOf(0)}
 val recent=history.distinct().mapNotNull{metadata[it]}.take(8)
 LaunchedEffect(channels.map{it.id},refresh) {
  channels=loadSubscribedChannels(prefs)
  if(channels.isEmpty()){uploads=emptyList();status="Follow channels to build your latest uploads feed."}
  else {
   status="Refreshing your feed…"
   try { uploads=withContext(Dispatchers.IO){channels.flatMap{channel->runCatching{LiveYouTubeSearch.latestUploads(channel.id)}.getOrDefault(emptyList())}.distinctBy{it.videoId}.take(30)};status="${uploads.size} latest uploads" }
   catch(e:Exception){status=e.localizedMessage?:"Could not refresh the feed."}
  }
 }
 LazyColumn(contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{
   Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{Text("Your subscriptions",color=ink,fontSize=19.sp,fontWeight=FontWeight.Bold);Text(status,color=muted,fontSize=10.sp)}
    TextButton(onClick={refresh++}){Text("Refresh",color=magenta)}
   }
  }
  if(channels.isEmpty())item{
   Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface).padding(16.dp)){
    Text("No channels followed yet.",color=ink,fontWeight=FontWeight.SemiBold)
    Text("Search for creators in Discover and subscribe to see their latest uploads here.",color=muted,fontSize=11.sp)
    TextButton(onClick=discover){Text("Find channels",color=magenta)}
   }
  }
  items(uploads,key={it.videoId}){video->
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(surface).clickable{play(Pick(video.videoId,video.title,video.creator,video.isMusic,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Latest upload"))}.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(raised),contentAlignment=Alignment.Center){Text("▶",color=mint)}
    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(video.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(video.creator+" · "+video.duration,color=muted,fontSize=10.sp,maxLines=1)}
    Text("Play",color=magenta,fontSize=11.sp)
   }
  }
  item{Shelf("Continue watching and listening","Recent",ink)}
  if(recent.isEmpty())item{Text("Recently played items will appear here.",color=muted,fontSize=11.sp)}
  items(recent,key={it.id}){pick->CardRow(pick,ink,muted,surface,saved,{play(pick)},{save(it)})}
 }
}
@Composable private fun LiveDiscover(ink:Color,muted:Color,surface:Color,saved:Set<String>,save:(String)->Unit,play:(YouTubeSearchResult)->Unit){
 var query by remember { mutableStateOf("") }
 var filter by remember { mutableStateOf("Videos") }
 var results by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
 var status by remember { mutableStateOf("Search videos, music or channels on YouTube.") }
 var loading by remember { mutableStateOf(false) }
 var selectedChannel by remember { mutableStateOf<YouTubeSearchResult?>(null) }
 val scope= rememberCoroutineScope()
 val context=androidx.compose.ui.platform.LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)}
 var playlists by remember{mutableStateOf(PlaylistStore.load(prefs))}
 var playlistTarget by remember{mutableStateOf<YouTubeSearchResult?>(null)}
 var newPlaylistName by remember{mutableStateOf("")}
 if(selectedChannel!=null) {
  ChannelDetails(selectedChannel!!,ink,muted,surface,{selectedChannel=null},play)
 } else {
  Column(Modifier.fillMaxSize().padding(16.dp)) {
   Text("Search YouTube",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Spacer(Modifier.height(10.dp))
   Row(verticalAlignment=Alignment.CenterVertically) {
    BasicTextField(query,{query=it},Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink,fontSize=15.sp),singleLine=true,decorationBox={inner->if(query.isEmpty())Text("Videos, songs, artists or channels",color=muted);inner()})
    Spacer(Modifier.width(8.dp))
    Button(enabled=query.isNotBlank()&&!loading,onClick={
     scope.launch {
      loading=true;status="Searching…"
      try { results=withContext(Dispatchers.IO){LiveYouTubeSearch.search(query,filter=="Music",filter=="Channels")};status="${results.size} results" }
      catch(e:Exception){results=emptyList();status=e.localizedMessage?: "Search failed."}
      finally {loading=false}
     }
    }) { Text(if(loading)"…" else "Search") }
   }
   Spacer(Modifier.height(8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("Videos","Music","Channels").forEach { FilterChip(selected=filter==it,onClick={filter=it;results=emptyList()},label={Text(it)}) }
   }
   Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=8.dp))
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    items(results,key={if(it.isChannel)it.channelId else it.videoId}) { result ->
     Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface).clickable{if(result.isChannel)selectedChannel=result else play(result)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(raised),contentAlignment=Alignment.Center){Text(if(result.isChannel)"◉" else if(result.isMusic)"♫" else "▶",color=mint,fontSize=20.sp)}
      Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
       Text(result.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
       Text(if(result.isChannel)result.creator.ifBlank{"YouTube channel"} else result.creator + if(result.duration.isNotBlank()) " · ${result.duration}" else "",color=muted,fontSize=11.sp,maxLines=1)
      }
      if(result.isChannel) Text("Open",color=magenta,fontSize=12.sp)
      else Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(result.videoId in saved)"♥" else "♡",color=magenta,modifier=Modifier.clickable{save(result.videoId)}.padding(4.dp));Text("＋",color=magenta,modifier=Modifier.clickable{playlistTarget=result}.padding(4.dp));Text("Play",color=magenta,fontSize=12.sp)}
     }
    }
   }
  }
 }
 if(playlistTarget!=null) {
  AlertDialog(
   onDismissRequest={playlistTarget=null;newPlaylistName=""},
   title={Text("Add to playlist")},
   text={
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
     OutlinedTextField(value=newPlaylistName,onValueChange={newPlaylistName=it},label={Text("New playlist name")},singleLine=true)
     if(playlists.isEmpty())Text("Create your first playlist above.",color=muted,fontSize=11.sp)
     playlists.forEach{playlist->TextButton(onClick={playlists=PlaylistStore.add(prefs,playlist.id,playlistTarget!!.videoId);playlistTarget=null}){Text("Add to ${playlist.name}",color=ink)}}
    }
   },
   confirmButton={TextButton(onClick={
    val target=playlistTarget
    if(target!=null&&newPlaylistName.isNotBlank()){
     playlists=PlaylistStore.create(prefs,newPlaylistName,target.videoId)
     newPlaylistName="";playlistTarget=null
    }
   },enabled=newPlaylistName.isNotBlank()){Text("Create and add")}},
   dismissButton={TextButton(onClick={playlistTarget=null;newPlaylistName=""}){Text("Close")}}
  )
 }
}

@Composable private fun ChannelDetails(channel:YouTubeSearchResult,ink:Color,muted:Color,surface:Color,back:()->Unit,play:(YouTubeSearchResult)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)}
 var subscriptions by remember{mutableStateOf(prefs.getStringSet("subscriptions",emptySet())?.toSet()?:emptySet())}
 var uploads by remember(channel.channelId){mutableStateOf<List<YouTubeSearchResult>>(emptyList())}
 var status by remember(channel.channelId){mutableStateOf("Loading latest uploads…")}
 var loading by remember(channel.channelId){mutableStateOf(false)}
 val subscribed=channel.channelId in subscriptions
 LaunchedEffect(channel.channelId) {
  loading=true
  try { uploads=withContext(Dispatchers.IO){LiveYouTubeSearch.latestUploads(channel.channelId)};status=if(uploads.isEmpty())"No public uploads were found." else "${uploads.size} latest uploads" }
  catch(e:Exception){status=e.localizedMessage?:"Could not load channel uploads."}
  finally {loading=false}
 }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   Text("← Back",color=muted,modifier=Modifier.clickable{back()}.padding(8.dp))
   Button(onClick={
    subscriptions=if(subscribed)subscriptions-channel.channelId else subscriptions+channel.channelId
    if(!subscribed)persistChannelMetadata(prefs,channel)
    prefs.edit().putStringSet("subscriptions",subscriptions).apply()
   }){Text(if(subscribed)"Subscribed" else "Subscribe")}
  }
  Text(channel.title,color=ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
  if(channel.creator.isNotBlank())Text(channel.creator,color=muted,fontSize=11.sp)
  Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=10.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(uploads,key={it.videoId}) { video ->
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface).clickable{play(video)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(raised),contentAlignment=Alignment.Center){Text("▶",color=mint)}
     Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(video.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(video.creator+" · "+video.duration,color=muted,fontSize=10.sp)}
     Text("Play",color=magenta,fontSize=11.sp)
    }
   }
  }
 }
}
@Composable private fun SubscriptionsScreen(ink:Color,muted:Color,surface:Color,play:(YouTubeSearchResult)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)}
 var channels by remember{mutableStateOf(loadSubscribedChannels(prefs))}
 var uploads by remember{mutableStateOf<List<YouTubeSearchResult>>(emptyList())}
 var status by remember{mutableStateOf("Latest uploads from channels you follow.")}
 var loading by remember{mutableStateOf(false)}
 LaunchedEffect(channels.map{it.id}) {
  loading=true
  try {
   uploads=withContext(Dispatchers.IO){channels.flatMap{channel->runCatching{LiveYouTubeSearch.latestUploads(channel.id)}.getOrDefault(emptyList())}.distinctBy{it.videoId}.take(60)}
   status=if(uploads.isEmpty())"No recent public uploads found." else "${uploads.size} recent uploads"
  } catch(e:Exception){status=e.localizedMessage?:"Could not refresh subscriptions."}
  finally{loading=false}
 }
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{
   Text("Subscriptions",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))
  }
  if(channels.isEmpty())item{Text("No subscriptions yet. Search for a channel in Discover and subscribe to it.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(surface).padding(16.dp))}
  items(channels,key={it.id}){channel->
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(surface).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text(channel.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold);if(channel.subscriberCount.isNotBlank())Text(channel.subscriberCount,color=muted,fontSize=10.sp)}
    TextButton(onClick={
     val ids=prefs.getStringSet("subscriptions",emptySet())?.toMutableSet()?:mutableSetOf()
     ids.remove(channel.id);prefs.edit().putStringSet("subscriptions",ids).apply();channels=channels.filterNot{it.id==channel.id}
    }){Text("Unsubscribe",color=muted,fontSize=10.sp)}
   }
  }
  item{Shelf("Latest uploads","From subscribed channels",ink)}
  if(uploads.isEmpty()&&!loading)item{Text("Upload feed is empty.",color=muted)}
  items(uploads,key={it.videoId}){video->
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface).clickable{play(video)}.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(45.dp).clip(RoundedCornerShape(9.dp)).background(raised),contentAlignment=Alignment.Center){Text("▶",color=mint)}
    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(video.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(video.creator+" · "+video.duration,color=muted,fontSize=10.sp,maxLines=1)}
    Text("Play",color=magenta,fontSize=11.sp)
   }
  }
 }
}

@Composable private fun DownloadsScreen(ink:Color,muted:Color,surface:Color,play:(OfflineMedia)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 var downloads by remember { mutableStateOf<List<OfflineMedia>>(emptyList()) }
 var workInfos by remember { mutableStateOf<List<androidx.work.WorkInfo>>(emptyList()) }
 LaunchedEffect(context) {
  while (true) {
   downloads=withContext(Dispatchers.IO){OfflineDownloads.list(context)}
   workInfos=withContext(Dispatchers.IO){runCatching{OfflineDownloads.workInfos(context)}.getOrDefault(emptyList())}
   kotlinx.coroutines.delay(1200)
  }
 }
 val working=workInfos.mapNotNull{work->work.tags.firstOrNull{tag->tag.startsWith("${OfflineDownloads.WORK_TAG}:")}?.substringAfter(":")?.let{id->id to work}}.toMap()
 val storageBytes=downloads.filter{it.complete}.sumOf{it.file.length()}
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item {
   Text("Offline downloads",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Text("${downloads.size} items · ${android.text.format.Formatter.formatShortFileSize(context,storageBytes)} used",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))
   Text("Files stay in Rush's private app storage. Download only content you are permitted to keep offline.",color=muted,fontSize=10.sp,modifier=Modifier.padding(top=7.dp))
  }
  if(downloads.isEmpty()) item {
   Text("Your offline library is empty. Open a video or music player and choose Download offline.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface).padding(18.dp))
  }
  items(downloads,key={it.id}) { media ->
   val work=working[media.id]
   val progress=work?.progress?.getInt("progress",0)?:0
   val active=work?.state==androidx.work.WorkInfo.State.RUNNING || work?.state==androidx.work.WorkInfo.State.ENQUEUED || work?.state==androidx.work.WorkInfo.State.BLOCKED
   Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text(media.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
    Text(media.creator+" · "+(if(media.isAudio)"Audio" else "Video")+" · "+media.quality+" · ."+media.extension,color=muted,fontSize=10.sp,maxLines=2)
    Text(when {
     media.complete -> "Ready offline · ${android.text.format.Formatter.formatShortFileSize(context,media.file.length())}"
     work?.state==androidx.work.WorkInfo.State.RUNNING -> "Downloading · $progress%"
     work?.state==androidx.work.WorkInfo.State.ENQUEUED || work?.state==androidx.work.WorkInfo.State.BLOCKED -> "Queued"
     work?.state==androidx.work.WorkInfo.State.FAILED || work?.state==androidx.work.WorkInfo.State.CANCELLED -> "Interrupted · resume to continue"
     else -> "Preparing download"
    },color=if(media.complete)mint else muted,fontSize=11.sp)
    if(active) LinearProgressIndicator(progress={progress/100f},modifier=Modifier.fillMaxWidth())
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     if(media.complete) Button(onClick={play(media)},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Play offline")}
     else if(active) OutlinedButton(onClick={OfflineDownloads.cancel(context,media.id)}){Text("Cancel")}
     else Button(onClick={OfflineDownloads.resume(context,media)},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Resume")}
     TextButton(onClick={OfflineDownloads.delete(context,media)}){Text("Delete",color=muted)}
    }
   }
  }
 }
}

@Composable private fun Library(saved:Set<String>,history:List<String>,metadata:Map<String,Pick>,prefs:android.content.SharedPreferences,ink:Color,muted:Color,surface:Color,play:(String)->Unit,remove:(String)->Unit){
 val all=metadata
 val kept=saved.mapNotNull{all[it]};val recent=history.distinct().mapNotNull{all[it]}
 var playlists by remember{mutableStateOf(PlaylistStore.load(prefs))}
 var showCreate by remember{mutableStateOf(false)}
 var playlistName by remember{mutableStateOf("")}
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Shelf("Playlists","Your collections",ink)
    TextButton(onClick={showCreate=true}){Text("＋ New",color=magenta)}
   }
  }
  if(playlists.isEmpty())item{Text("Create a playlist, then add videos or music from Discover.",color=muted,fontSize=11.sp)}
  items(playlists,key={it.id}){playlist->
   Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text(playlist.name,color=ink,fontSize=14.sp,fontWeight=FontWeight.Bold);Text("${playlist.itemIds.size} items",color=muted,fontSize=10.sp)}
     TextButton(onClick={playlists=PlaylistStore.delete(prefs,playlist.id)}){Text("Delete",color=muted,fontSize=10.sp)}
    }
    val tracks=playlist.itemIds.mapNotNull{all[it]}
    if(tracks.isEmpty())Text("No items yet. Add videos or music from Discover.",color=muted,fontSize=10.sp)
    tracks.forEach{track->
     Row(Modifier.fillMaxWidth().clickable{play(track.id)}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
      Text(track.title,color=ink,fontSize=11.sp,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
      Text("▶",color=magenta,modifier=Modifier.padding(horizontal=6.dp))
      Text("×",color=muted,modifier=Modifier.clickable{playlists=PlaylistStore.remove(prefs,playlist.id,track.id)}.padding(5.dp))
     }
    }
   }
  }
  item{Shelf("Saved items","Bookmarks",ink)}
  if(kept.isEmpty())item{Text("Saved videos and music will appear here.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface).padding(18.dp))}
  items(kept){pick->CardRow(pick,ink,muted,surface,saved,{play(pick.id)},{remove(pick.id)})}
  item{Shelf("Recently played","History",ink)}
  if(recent.isEmpty())item{Text("Your viewing and listening history will appear here.",color=muted)}
  items(recent){pick->CardRow(pick,ink,muted,surface,saved,{play(pick.id)},{})}
 }
 if(showCreate)AlertDialog(
  onDismissRequest={showCreate=false;playlistName=""},
  title={Text("Create playlist")},
  text={OutlinedTextField(value=playlistName,onValueChange={playlistName=it},label={Text("Playlist name")},singleLine=true)},
  confirmButton={TextButton(enabled=playlistName.isNotBlank(),onClick={playlists=PlaylistStore.create(prefs,playlistName);playlistName="";showCreate=false}){Text("Create")}},
  dismissButton={TextButton(onClick={showCreate=false;playlistName=""}){Text("Cancel")}}
 )
}
@Composable private fun RushPlayer(item:Pick,light:Boolean,back:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current;val scope=rememberCoroutineScope()
 var controller by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
 val controllerFuture = remember(context) {
  val token = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, RushPlaybackService::class.java))
  androidx.media3.session.MediaController.Builder(context, token).buildAsync()
 }
 DisposableEffect(controllerFuture) {
  controllerFuture.addListener({ controller = runCatching { controllerFuture.get() }.getOrNull() }, context.mainExecutor)
  onDispose { androidx.media3.session.MediaController.releaseFuture(controllerFuture) }
 }
 var url by remember(item.id){mutableStateOf(item.id.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}.orEmpty())};var audio by remember(item.id){mutableStateOf(item.music)}
 var quality by remember(item.id){mutableIntStateOf(0)}
 var speed by remember(item.id){mutableFloatStateOf(1f)}
 var captionsEnabled by remember(item.id){mutableStateOf(true)}
 var status by remember(item.id){mutableStateOf(if(item.localPath!=null)"Downloaded file ready." else "Paste a YouTube link or video ID to play.")}
 val ink=if(light)Color(0xFF241B32)else paper;val muted=if(light)Color(0xFF70677D)else mutedDark;val surface=if(light)Color.White else panelDark

 Column(Modifier.fillMaxSize().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("← Back",color=muted,modifier=Modifier.clickable{back()}.padding(8.dp));Text(if(audio)"Music player"else"Video player",color=mint)}
  Spacer(Modifier.weight(.2f))
  if(audio)Disc(item,Modifier.size(240.dp))else Cover(item,Modifier.fillMaxWidth().height(190.dp))
  Text(item.title,color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(item.creator,color=muted)
  if(item.localPath==null) {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=audio,onClick={audio=true},label={Text("Audio only")});FilterChip(selected=!audio,onClick={audio=false},label={Text("Video")})}
   if(!audio) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0 to "Best",720 to "720p",480 to "480p",360 to "360p").forEach{(height,label)->FilterChip(selected=quality==height,onClick={quality=height},label={Text(label)})}} else Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0 to "Best",160 to "160 kbps",96 to "96 kbps").forEach{(bitrate,label)->FilterChip(selected=quality==bitrate,onClick={quality=bitrate},label={Text(label)})}}
   BasicTextField(url,{url=it},modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink),singleLine=true,decorationBox={inner->if(url.isEmpty())Text("YouTube URL or video ID",color=muted);inner()})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){
   Text("Speed",color=muted,fontSize=10.sp)
   listOf(0.75f,1f,1.25f,1.5f,2f).forEach{value->
    FilterChip(selected=speed==value,onClick={speed=value;controller?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(value))},label={Text("${value}x",fontSize=10.sp)})
   }
  }
  FilterChip(selected=captionsEnabled,onClick={
   captionsEnabled=!captionsEnabled
   controller?.let{it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,!captionsEnabled).build()}
  },label={Text(if(captionsEnabled)"Captions enabled when available" else "Captions off")})
  Button(onClick={
   val activePlayer=controller
   if(activePlayer==null){status="Connecting to playback service…"}
   else scope.launch{
    status="Resolving stream…"
    try{
     val stream=if(item.localPath!=null)java.io.File(item.localPath).toURI().toString() else withContext(Dispatchers.IO){LibreTubePlayback.resolveMedia(url,audio,if(audio)null else quality.takeIf{it>0},if(audio)quality.takeIf{it>0} else null).url}
     activePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(stream));activePlayer.prepare();activePlayer.play();status=if(item.localPath!=null)"Playing offline" else "Stream ready"
    }catch(e:Exception){status=e.localizedMessage?:"Unable to load this stream."}
   }
  },enabled=controller!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text(if(item.localPath!=null)"Play offline" else "Load and play",color=Color.White)}
  if(item.localPath==null) Button(onClick={scope.launch{
   status="Preparing offline download…"
   try{
    val media=withContext(Dispatchers.IO){LibreTubePlayback.resolveMedia(url,audio,if(audio)null else quality.takeIf{it>0},if(audio)quality.takeIf{it>0} else null)}
    withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,item.id,item.title,item.creator,audio,media)}
    status="Download queued · ${media.qualityLabel} · .${media.extension}"
   }catch(e:Exception){status=e.localizedMessage?:"Could not start download."}
  }},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=raised)){Text("Download offline",color=ink)}
  androidx.compose.ui.viewinterop.AndroidView(factory={ctx->androidx.media3.ui.PlayerView(ctx).apply{this.player=controller;useController=true}},update={it.player=controller},modifier=Modifier.fillMaxWidth().height(if(audio)70.dp else 180.dp))
  Text(status,color=muted,fontSize=11.sp);Text("Use offline downloads only where permitted. Formats and quality depend on the source.",color=muted,fontSize=10.sp)
  Spacer(Modifier.weight(.5f))
 }
}
@Composable private fun CardRow(p:Pick,ink:Color,muted:Color,surface:Color,saved:Set<String>,play:(Pick)->Unit,save:(String)->Unit){
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(surface).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
  Cover(p,Modifier.size(78.dp).clickable{play(p)})
  Column(Modifier.weight(1f).padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(if(p.music)"Music"else"Video",color=mint,fontSize=9.sp);Text(p.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=2);Text(p.creator,color=muted,fontSize=11.sp);Text(p.reason,color=muted,fontSize=10.sp)}
  Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(p.id in saved)"♥"else"♡",color=magenta,fontSize=19.sp,modifier=Modifier.clickable{save(p.id)}.padding(5.dp));Text("▶",color=magenta,modifier=Modifier.clickable{play(p)}.padding(5.dp))}
 }
}
@Composable private fun Cover(p:Pick,m:Modifier=Modifier){Box(m.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(p.colors)),contentAlignment=Alignment.Center){Text(p.title.take(1),color=Color.White.copy(alpha=.2f),fontSize=72.sp,fontWeight=FontWeight.Black);Text(if(p.music)"♫"else"▷",color=Color.White,fontSize=30.sp)}}
@Composable private fun Disc(p:Pick,m:Modifier=Modifier){Box(m.clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF5D526B),Color(0xFF17131F),Color(0xFF050408)))),contentAlignment=Alignment.Center){Box(Modifier.fillMaxSize(.48f).clip(CircleShape).background(Brush.linearGradient(p.colors)),contentAlignment=Alignment.Center){Box(Modifier.size(9.dp).clip(CircleShape).background(bgDark))}}}
@Composable private fun Shelf(title:String,detail:String,ink:Color){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,color=ink,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(detail,color=mutedDark,fontSize=10.sp)}}
