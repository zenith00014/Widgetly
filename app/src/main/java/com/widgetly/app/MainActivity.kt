package com.widgetly.app

import android.content.Context
import android.annotation.SuppressLint
import android.content.ContextWrapper
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

private val bgDark=Color(0xFF0E0E0E)
private val panelDark=Color(0xFF181818)
private val raised=Color(0xFF242424)
private val magenta=Color(0xFFFF6B3D)
private val mint=Color(0xFFFF6B3D)
private val paper=Color(0xFFF2F2EE)
private val lilac=Color(0xFFCDC6B3)
private val mutedDark=Color(0xFFA8A8A2)

private fun Context.activityOrNull():android.app.Activity?=when(this){is android.app.Activity->this;is ContextWrapper->baseContext.activityOrNull();else->null}

@androidx.media3.common.util.UnstableApi
private fun androidx.media3.ui.PlayerView.prepareForPlayback(
 player: androidx.media3.common.Player?,
 keepScreenOn: Boolean
): androidx.media3.ui.PlayerView {
 this.player=player
 useController=true
 controllerAutoShow=true
 setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
 setKeepContentOnPlayerReset(true)
 setShowFastForwardButton(true)
 setShowRewindButton(true)
 setShowPreviousButton(true)
 setShowNextButton(true)
 setShowSubtitleButton(false)
 this.keepScreenOn=keepScreenOn
 return this
}

private data class Pick(val id:String,val title:String,val creator:String,val music:Boolean,val colors:List<Color>,val reason:String,val localPath:String?=null,val sourceId:String?=null)
private data class SubscribedChannel(val id:String,val title:String,val subscriberCount:String)
class MainActivity:ComponentActivity(){
 private val openDownloadsRequest=androidx.compose.runtime.mutableIntStateOf(0)
 private val pipModeState=androidx.compose.runtime.mutableStateOf(false)
 private val pipEligible=androidx.compose.runtime.mutableStateOf(false)
 private var pipAspectRatio=android.util.Rational(16,9)
 private lateinit var webView:android.webkit.WebView
 private lateinit var rushWebBridge: RushWebBridge
 fun setPipEligible(eligible:Boolean){
  pipEligible.value=eligible
  if(android.os.Build.VERSION.SDK_INT>=26){
   val builder=android.app.PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio)
   if(android.os.Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(eligible)
   setPictureInPictureParams(builder.build())
  }
 }
 fun updatePipAspectRatio(width:Int,height:Int){
  if(android.os.Build.VERSION.SDK_INT<26||width<=0||height<=0)return
  val ratio=(width.toFloat()/height).coerceIn(1f/2.39f,2.39f)
  pipAspectRatio=android.util.Rational((ratio*1000).toInt().coerceAtLeast(1),1000)
  if(pipEligible.value){
   val builder=android.app.PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio)
   if(android.os.Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(true)
   setPictureInPictureParams(builder.build())
  }
 }
 fun enterPlayerPip(){
  if(android.os.Build.VERSION.SDK_INT>=26&&pipEligible.value){
   val builder=android.app.PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio)
   if(android.os.Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(true)
   enterPictureInPictureMode(builder.build())
  }
 }
 override fun onUserLeaveHint(){
  super.onUserLeaveHint()
  if(android.os.Build.VERSION.SDK_INT>=26&&android.os.Build.VERSION.SDK_INT<31&&pipEligible.value)enterPlayerPip()
 }
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=android.graphics.Color.rgb(14,14,14)
  window.navigationBarColor=android.graphics.Color.rgb(14,14,14)
  window.decorView.systemUiVisibility=0
  webView=android.webkit.WebView(this).apply{
   setBackgroundColor(android.graphics.Color.rgb(205,198,179))
   isVerticalScrollBarEnabled=false
   isHorizontalScrollBarEnabled=false
   overScrollMode=android.view.View.OVER_SCROLL_NEVER
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   settings.loadsImagesAutomatically=true
   webViewClient=android.webkit.WebViewClient()
   webChromeClient=android.webkit.WebChromeClient()
   rushWebBridge=RushWebBridge(this@MainActivity,this)
   addJavascriptInterface(rushWebBridge,"RushNative")
  }
  setContentView(webView)
  val html=assets.open("rush.html").bufferedReader().use{it.readText()}
  webView.loadDataWithBaseURL("https://rush.local/",html,"text/html","UTF-8",null)
  onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){
   override fun handleOnBackPressed(){
    if(webView.canGoBack())webView.goBack()
    else webView.evaluateJavascript("location.hash='#home'",null)
   }
  })
 }
 override fun onNewIntent(intent:android.content.Intent){
  super.onNewIntent(intent)
  setIntent(intent)
  if(intent.getBooleanExtra("open_downloads",false))webView.evaluateJavascript("location.hash='#videos'",null)
 }
 override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean,newConfig:android.content.res.Configuration){
  super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
  pipModeState.value=isInPictureInPictureMode
 }
 override fun onDestroy(){
  if(::rushWebBridge.isInitialized)rushWebBridge.release()
  if(::webView.isInitialized){webView.stopLoading();webView.destroy()}
  super.onDestroy()
 }
}
@Composable private fun RushApp(prefs:android.content.SharedPreferences,openDownloadsRequest:Int=0,isInPipMode:Boolean=false){
 val activity=androidx.compose.ui.platform.LocalContext.current.activityOrNull()
 var tab by remember{mutableStateOf(if(openDownloadsRequest>0)"Downloads" else "Home")};var light by remember{mutableStateOf(prefs.getBoolean("light",false))}
 var active by remember{mutableStateOf<Pick?>(null)}
 var playbackQueue by remember{mutableStateOf<List<Pick>>(emptyList())}
 var queuedIds by remember{mutableStateOf(QueueStore.load(prefs))}
 var saved by remember{mutableStateOf(prefs.getStringSet("saved",emptySet())?.toSet()?:emptySet())}
 var history by remember{mutableStateOf(loadHistory(prefs))}
 var metadata by remember{mutableStateOf(loadPickMetadata(prefs))}
 LaunchedEffect(openDownloadsRequest){if(openDownloadsRequest>0){tab="Downloads";active=null;playbackQueue=emptyList()}}
 val storeResult:(YouTubeSearchResult)->Unit={result->
  val pick=result.toPick()
  persistPickMetadata(prefs,pick)
  metadata=metadata+(pick.id to pick)
 }
 val saveResult:(YouTubeSearchResult)->Unit={result->
  storeResult(result)
  saved=toggle(saved,result.videoId)
  prefs.edit().putStringSet("saved",saved).apply()
 }
 val playResult:(YouTubeSearchResult)->Unit={result->
  val pick=result.toPick()
  storeResult(result)
  active=pick
  playbackQueue=emptyList()
  history=listOf(pick.id)+history.filterNot{x->x==pick.id}
  persistHistory(prefs,history)
 }
 SideEffect{(activity as? MainActivity)?.setPipEligible(active!=null&&active?.music==false)}
 val bg=if(light)lilac else bgDark;val surface=if(light)Color.White else panelDark
 val ink=if(light)Color(0xFF241B32) else paper;val muted=if(light)Color(0xFF70677D) else mutedDark
 MaterialTheme(colorScheme=if(light)lightColorScheme(primary=magenta,background=bg,surface=surface,onSurface=ink)else darkColorScheme(primary=magenta,secondary=magenta,background=bg,surface=surface,onSurface=ink,onPrimary=bgDark),shapes=Shapes(extraSmall=RoundedCornerShape(0.dp),small=RoundedCornerShape(0.dp),medium=RoundedCornerShape(0.dp),large=RoundedCornerShape(0.dp),extraLarge=RoundedCornerShape(0.dp))){
  Surface(Modifier.fillMaxSize(),color=bg){Column(Modifier.fillMaxSize()){
   if(active!=null)RushPlayer(active!!,light,{active=null;playbackQueue=emptyList()},playbackQueue,{id->
    history=listOf(id)+history.filterNot{it==id}
    persistHistory(prefs,history)
   },isInPipMode)
   else{
    Row(Modifier.fillMaxWidth().padding(start=18.dp,end=12.dp,top=16.dp,bottom=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
       Text("RUSH",color=ink,fontSize=34.sp,fontWeight=FontWeight.Black,letterSpacing=(-1.5).sp,lineHeight=34.sp)
       Box(Modifier.size(11.dp).background(magenta).border(1.dp,ink))
      }
      Text(when(tab){"Discover"->"FIND YOUR NEXT FAVOURITE";"Downloads"->"OFFLINE LIBRARY";"Subscriptions"->"LATEST UPLOADS";"Library"->"YOUR COLLECTION";else->"YOUR DAILY SIGNAL"},color=muted,fontSize=9.sp,letterSpacing=1.2.sp)
     }
     TextButton(onClick={light=!light;prefs.edit().putBoolean("light",light).apply()},modifier=Modifier.border(1.dp,ink).background(surface)){
      Text(if(light)"DARK" else "LIGHT",color=ink,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
     }
    }
    Row(Modifier.fillMaxWidth().background(if(light)Color(0xFF241B32) else paper).padding(horizontal=16.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
     Text("NOW TRENDING  ✦  MONZA LAP RECORD  ✦  CASSETTE REVIVAL  ✦  OFFSIDE RULE  ✦  NIGHT DRIVE TAPES",color=if(light)paper else bgDark,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=0.8.sp,maxLines=1)
    }
    Box(Modifier.weight(1f)){
     when(tab){
      "Discover"->LiveDiscover(ink,muted,surface,saved,storeResult,saveResult,playResult,{result->storeResult(result);queuedIds=QueueStore.add(prefs,result.videoId)})
      "Downloads"->DownloadsScreen(ink,muted,surface,{media->
       val pick=Pick(media.id,media.title,media.creator,media.isAudio,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Downloaded for offline playback",media.file.absolutePath,media.sourceId)
       active=pick;playbackQueue=emptyList()
       persistPickMetadata(prefs,pick);metadata=metadata+(media.id to pick)
       history=listOf(media.id)+history.filterNot{x->x==media.id};persistHistory(prefs,history)
      },{items->
       val queue=items.filter{it.complete}.map{media->Pick(media.id,media.title,media.creator,media.isAudio,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Downloaded for offline playback",media.file.absolutePath,media.sourceId)}
       queue.forEach{pick->persistPickMetadata(prefs,pick);metadata=metadata+(pick.id to pick)}
       playbackQueue=queue
       queue.firstOrNull()?.let{first->active=first;history=listOf(first.id)+history.filterNot{x->x==first.id};persistHistory(prefs,history)}
      })
      "Subscriptions"->SubscriptionsScreen(ink,muted,surface,{result->val pick=Pick(result.videoId,result.title,result.creator,result.isMusic,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Latest upload");persistPickMetadata(prefs,pick);metadata=metadata+(pick.id to pick);active=pick;history=listOf(pick.id)+history.filterNot{x->x==pick.id};persistHistory(prefs,history)})
      "Library"->Library(saved,history,metadata,prefs,queuedIds,ink,muted,surface,{id->metadata[id]?.let{
       playbackQueue=emptyList();active=it;history=listOf(id)+history.filterNot{x->x==id};persistHistory(prefs,history)
      }},{id->saved=saved-id;prefs.edit().putStringSet("saved",saved).apply()},{queue->
       playbackQueue=queue;queuedIds=queue.map{it.id};QueueStore.replace(prefs,queuedIds)
       queue.firstOrNull()?.let{first->active=first;history=listOf(first.id)+history.filterNot{x->x==first.id};persistHistory(prefs,history)}
      },{ids->queuedIds=ids;QueueStore.replace(prefs,ids)},{history=emptyList();persistHistory(prefs,history)})
      else->Home(ink,muted,surface,prefs,saved,metadata,history,{pick->persistPickMetadata(prefs,pick);metadata=metadata+(pick.id to pick);active=pick;history=listOf(pick.id)+history.filterNot{x->x==pick.id};persistHistory(prefs,history)},{id->saved=toggle(saved,id);prefs.edit().putStringSet("saved",saved).apply()},{tab="Discover"})
     }
    }
    history.firstOrNull()?.let{metadata[it]}?.let{recent->Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp).clip(RoundedCornerShape(0.dp)).background(surface).clickable{active=recent}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Cover(recent,Modifier.size(40.dp));Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text("PICK UP WHERE YOU LEFT OFF",fontSize=9.sp,color=muted);Text(recent.title,fontSize=13.sp,color=ink)};Text("▶",color=magenta)}}
    NavigationBar(containerColor=if(light)Color.White else bgDark,tonalElevation=0.dp,modifier=Modifier.border(width=1.dp,color=ink.copy(alpha=.24f))){listOf("Home","Discover","Subscriptions","Downloads","Library").forEach{dest->NavigationBarItem(selected=tab==dest,onClick={tab=dest},icon={Text(when(dest){"Home"->"⌂";"Discover"->"⌕";"Downloads"->"⇩";"Subscriptions"->"◉";else->"▣"},fontSize=19.sp,fontWeight=FontWeight.Bold)},label={Text(dest.uppercase(),fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=.4.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=bgDark,selectedTextColor=magenta,indicatorColor=magenta,unselectedIconColor=muted,unselectedTextColor=muted))}}
   }
  }}
 }
}
private fun toggle(s:Set<String>,id:String)=if(id in s)s-id else s+id
private fun Pick.toMediaMetadata(
 playbackInput:String?=null,
 audioOnly:Boolean=music,
 quality:Int=0,
 preferMp4:Boolean=false
):androidx.media3.common.MediaMetadata{
 val builder=androidx.media3.common.MediaMetadata.Builder().setTitle(title).setArtist(creator)
 val videoId=(sourceId?:id).takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}
 if(videoId!=null)builder.setArtworkUri(android.net.Uri.parse("https://i.ytimg.com/vi/$videoId/hqdefault.jpg"))
 builder.setExtras(PlaybackMetadata.extras(playbackInput?.takeIf{it.isNotBlank()}?:sourceId?:id,audioOnly,quality,preferMp4))
 return builder.build()
}
private fun YouTubeSearchResult.toPick()=Pick(
 videoId,
 title,
 creator,
 isMusic,
 listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),
 if(isMusic)"YouTube Music result" else "YouTube result"
)
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
  val localPath=item.optString("localPath").takeIf{it.isNotBlank()&&java.io.File(it).exists()}
  result[id]=Pick(id,item.optString("title"),item.optString("creator"),item.optBoolean("music"),listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)), "Saved from YouTube",localPath,item.optString("sourceId").takeIf{it.isNotBlank()})
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
 root.put(pick.id,org.json.JSONObject().put("title",pick.title).put("creator",pick.creator).put("music",pick.music).put("localPath",pick.localPath.orEmpty()).put("sourceId",pick.sourceId.orEmpty()))
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
   catch(e:Exception){status=playbackErrorMessage(e,"Could not refresh the feed.")}
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
   Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(16.dp)){
    Text("No channels followed yet.",color=ink,fontWeight=FontWeight.SemiBold)
    Text("Search for creators in Discover and subscribe to see their latest uploads here.",color=muted,fontSize=11.sp)
    TextButton(onClick=discover){Text("Find channels",color=magenta)}
   }
  }
  items(uploads,key={it.videoId}){video->
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).clickable{play(Pick(video.videoId,video.title,video.creator,video.isMusic,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Latest upload"))}.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
    YouTubeThumbnail(video.thumbnailUrl,video.videoId,Modifier.size(52.dp),placeholder="▶")
    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(video.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(video.creator+" · "+video.duration,color=muted,fontSize=10.sp,maxLines=1)}
    Text("Play",color=magenta,fontSize=11.sp)
   }
  }
  item{Shelf("Continue watching and listening","Recent",ink)}
  if(recent.isEmpty())item{Text("Recently played items will appear here.",color=muted,fontSize=11.sp)}
  items(recent,key={it.id}){pick->CardRow(pick,ink,muted,surface,saved,{play(pick)},{save(it)})}
 }
}
@Composable private fun LiveDiscover(ink:Color,muted:Color,surface:Color,saved:Set<String>,store:(YouTubeSearchResult)->Unit,save:(YouTubeSearchResult)->Unit,play:(YouTubeSearchResult)->Unit,enqueue:(YouTubeSearchResult)->Unit){
 var query by remember { mutableStateOf("") }
 var filter by remember { mutableStateOf("Videos") }
 var results by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
 var status by remember { mutableStateOf("Search videos, music or channels on YouTube.") }
 var loading by remember { mutableStateOf(false) }
 var loadingMore by remember { mutableStateOf(false) }
 var continuation by remember { mutableStateOf<String?>(null) }
 var selectedChannel by remember { mutableStateOf<YouTubeSearchResult?>(null) }
 var selectedMusicCollection by remember { mutableStateOf<YouTubeSearchResult?>(null) }
 var selectedVideoPlaylist by remember { mutableStateOf<YouTubeSearchResult?>(null) }
 val scope= rememberCoroutineScope()
 val context=androidx.compose.ui.platform.LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)}
 var playlists by remember{mutableStateOf(PlaylistStore.load(prefs))}
 var playlistTarget by remember{mutableStateOf<YouTubeSearchResult?>(null)}
 var newPlaylistName by remember{mutableStateOf("")}
 if(selectedChannel!=null) {
  ChannelDetails(selectedChannel!!,ink,muted,surface,{selectedChannel=null},play)
 } else if(selectedMusicCollection!=null) {
  CollectionDetails(selectedMusicCollection!!,ink,muted,surface,{selectedMusicCollection=null},play,enqueue,store)
 } else if(selectedVideoPlaylist!=null) {
  CollectionDetails(selectedVideoPlaylist!!,ink,muted,surface,{selectedVideoPlaylist=null},play,enqueue,store)
 } else {
  Column(Modifier.fillMaxSize().padding(16.dp)) {
   Text("Search YouTube",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Spacer(Modifier.height(10.dp))
   Row(verticalAlignment=Alignment.CenterVertically) {
    BasicTextField(query,{query=it},Modifier.weight(1f).clip(RoundedCornerShape(0.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink,fontSize=15.sp),singleLine=true,decorationBox={inner->if(query.isEmpty())Text("Videos, songs, artists or channels",color=muted);inner()})
    Spacer(Modifier.width(8.dp))
    Button(enabled=query.isNotBlank()&&!loading&&!loadingMore,onClick={
     scope.launch {
      loading=true;loadingMore=false;continuation=null;results=emptyList();status="Searching…"
      try {
       val page=withContext(Dispatchers.IO){LiveYouTubeSearch.searchPage(query,filter=="Music",filter=="Channels",filter=="Playlists")}
       results=page.items;continuation=page.continuation
       status="${results.size} results"
      }
      catch(e:Exception){results=emptyList();continuation=null;status=e.localizedMessage?: "Search failed."}
      finally {loading=false}
     }
    }) { Text(if(loading)"…" else "Search") }
   }
   Spacer(Modifier.height(8.dp))
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("Videos","Music","Channels","Playlists").forEach { FilterChip(selected=filter==it,onClick={filter=it;results=emptyList();continuation=null;status="Search videos, music or channels on YouTube."},label={Text(it)}) }
   }
   Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=8.dp))
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    items(results,key={when{it.isChannel->"channel:"+it.channelId;it.isCollection->"collection:"+it.collectionId;else->it.videoId}}) { result ->
     Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).clickable{when{result.isChannel->selectedChannel=result;result.isCollection&&result.isMusic->selectedMusicCollection=result;result.isCollection->selectedVideoPlaylist=result;else->play(result)}}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
      YouTubeThumbnail(result.thumbnailUrl,result.videoId,Modifier.size(72.dp,54.dp),placeholder=if(result.isChannel)"◉" else if(result.isCollection)"▤" else if(result.isMusic)"♫" else "▶")
      Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
       Text(result.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
       Text(when{result.isChannel->listOf(result.creator,result.videoCount.takeIf{it.isNotBlank()}).filterNotNull().filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"YouTube channel"};result.isCollection->result.creator.ifBlank{"Artist / album"};else->result.creator + if(result.duration.isNotBlank()) " · ${result.duration}" else ""},color=muted,fontSize=11.sp,maxLines=1)
       if(result.isChannel&&result.description.isNotBlank())Text(result.description,color=muted,fontSize=10.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
      }
      if(result.isChannel||result.isCollection) Text("Open",color=magenta,fontSize=12.sp)
      else Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(result.videoId in saved)"♥" else "♡",color=magenta,modifier=Modifier.clickable{save(result)}.padding(4.dp));Text("＋",color=magenta,modifier=Modifier.clickable{playlistTarget=result}.padding(4.dp));Text("Queue ↥",color=magenta,fontSize=10.sp,modifier=Modifier.clickable{enqueue(result)}.padding(4.dp));Text("⇩",color=magenta,fontSize=14.sp,modifier=Modifier.clickable{
       scope.launch{
        status="Preparing download…"
        try{
         val resolved=withContext(Dispatchers.IO){resolvePlaybackMedia(result.videoId,result.isMusic)}
         withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,result.videoId,result.title,result.creator,result.isMusic,resolved)}
         status="Download queued: "+result.title
        }catch(e:Exception){status=playbackErrorMessage(e,"Could not queue this download.")}
       }
      }.padding(4.dp));Text("Play",color=magenta,fontSize=12.sp)}
     }
    }
    if(continuation!=null||loadingMore)item {
     Button(enabled=!loadingMore&&!loading,onClick={
      val token=continuation
      if(token!=null)scope.launch{
       loadingMore=true;status="Loading more results…"
       try{
        val page=withContext(Dispatchers.IO){LiveYouTubeSearch.continueSearch(token,filter=="Music",filter=="Channels",filter=="Playlists")}
        val merged=(results+page.items).distinctBy{result->when{result.isChannel->"channel:"+result.channelId;result.isCollection->"collection:"+result.collectionId;else->result.videoId}}
        results=merged;continuation=page.continuation;status="${results.size} results"
       }catch(e:Exception){status=playbackErrorMessage(e,"Could not load more results.")}
       finally{loadingMore=false}
      }
     },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=raised)){
      Text(if(loadingMore)"Loading…" else "Load more results",color=ink)
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
     playlists.forEach{playlist->TextButton(onClick={store(playlistTarget!!);playlists=PlaylistStore.add(prefs,playlist.id,playlistTarget!!.videoId);playlistTarget=null}){Text("Add to ${playlist.name}",color=ink)}}
    }
   },
   confirmButton={TextButton(onClick={
    val target=playlistTarget
    if(target!=null&&newPlaylistName.isNotBlank()){
     store(target)
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
 var details by remember(channel.channelId){mutableStateOf(channel)}
 var uploads by remember(channel.channelId){mutableStateOf<List<YouTubeSearchResult>>(emptyList())}
 var status by remember(channel.channelId){mutableStateOf("Loading channel details and latest uploads…")}
 var loading by remember(channel.channelId){mutableStateOf(false)}
 val subscribed=details.channelId in subscriptions || channel.channelId in subscriptions
 LaunchedEffect(channel.channelId) {
  loading=true
  try {
   val loaded=withContext(Dispatchers.IO){LiveYouTubeSearch.channelDetails(channel)}
   details=loaded
   uploads=withContext(Dispatchers.IO){LiveYouTubeSearch.latestUploads(loaded.channelId)}
   status=if(uploads.isEmpty())"No public uploads were found." else "${uploads.size} latest uploads"
  }
  catch(e:Exception){status=playbackErrorMessage(e,"Could not load channel details or uploads.")}
  finally {loading=false}
 }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   Text("← Back",color=muted,modifier=Modifier.clickable{back()}.padding(8.dp))
   Button(onClick={
    subscriptions=if(subscribed)subscriptions-details.channelId-channel.channelId else subscriptions+details.channelId
    if(!subscribed)persistChannelMetadata(prefs,details)
    prefs.edit().putStringSet("subscriptions",subscriptions).apply()
   }){Text(if(subscribed)"Subscribed" else "Subscribe")}
  }
  Text(details.title,color=ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
  if(details.creator.isNotBlank()||details.videoCount.isNotBlank())Text(listOf(details.creator,details.videoCount.takeIf{it.isNotBlank()}).filterNotNull().filter{it.isNotBlank()}.joinToString(" · "),color=muted,fontSize=11.sp)
  if(details.description.isNotBlank())Text(details.description,color=ink.copy(alpha=.82f),fontSize=12.sp,lineHeight=17.sp)
  Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=10.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(uploads,key={it.videoId}) { video ->
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).clickable{play(video)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
     YouTubeThumbnail(video.thumbnailUrl,video.videoId,Modifier.size(46.dp),placeholder="▶")
     Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(video.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(video.creator+" · "+video.duration,color=muted,fontSize=10.sp)}
     Text("Play",color=magenta,fontSize=11.sp)
    }
   }
  }
 }
}
private suspend fun queueVideoDownloads(context:Context,tracks:List<YouTubeSearchResult>):Int{
 var queued=0
 for(track in tracks){
  try{
   val media=withContext(Dispatchers.IO){resolvePlaybackMedia(track.videoId,false)}
   withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,track.videoId,track.title,track.creator,false,media)}
   queued++
  }catch(_:Exception){}
 }
 return queued
}

private suspend fun queueMusicDownloads(context:Context,tracks:List<YouTubeSearchResult>,mp3:Boolean):Int{
 var queued=0
 for(track in tracks){
  try{
   val media=withContext(Dispatchers.IO){resolvePlaybackMedia(track.videoId,true)}
   withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,track.videoId,track.title,track.creator,true,media,transcodeToMp3=mp3)}
   queued++
  }catch(_:Exception){}
 }
 return queued
}

private suspend fun queueLocalPickMp3Downloads(context:Context,tracks:List<Pick>):Int{
 var queued=0
 for(track in tracks){
  try{
   val media=withContext(Dispatchers.IO){resolvePlaybackMedia(track.id,true)}
   withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,track.id,track.title,track.creator,true,media,transcodeToMp3=true)}
   queued++
  }catch(_:Exception){}
 }
 return queued
}

@Composable private fun CollectionDetails(
 collection:YouTubeSearchResult,
 ink:Color,
 muted:Color,
 surface:Color,
 back:()->Unit,
 play:(YouTubeSearchResult)->Unit,
 enqueue:(YouTubeSearchResult)->Unit,
 store:(YouTubeSearchResult)->Unit
){
 val context=androidx.compose.ui.platform.LocalContext.current
 val scope=rememberCoroutineScope()
 var tracks by remember(collection.collectionId){mutableStateOf<List<YouTubeSearchResult>>(emptyList())}
 var status by remember(collection.collectionId){mutableStateOf(if(collection.isMusic)"Loading songs…" else "Loading playlist…")}
 var loading by remember(collection.collectionId){mutableStateOf(false)}
 var downloadMessage by remember(collection.collectionId){mutableStateOf("")}
 var libraryMessage by remember(collection.collectionId){mutableStateOf("")}
 var downloading by remember(collection.collectionId){mutableStateOf(false)}
 LaunchedEffect(collection.collectionId){
  loading=true
  try{
   tracks=withContext(Dispatchers.IO){
    if(collection.isMusic)LiveYouTubeSearch.browseMusicCollection(collection.collectionId)
    else LiveYouTubeSearch.browseVideoPlaylist(collection.collectionId)
   }
   status=if(tracks.isEmpty())"No playable items were found in this playlist." else "${tracks.size} items"
  }catch(e:Exception){status=playbackErrorMessage(e,"Could not load this music collection.")}
  finally{loading=false}
 }
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("← Back",color=muted,modifier=Modifier.clickable{back()}.padding(8.dp))
  Text(collection.title,color=ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
  Text(collection.creator,color=muted,fontSize=11.sp)
  Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=10.dp))
  if(tracks.isNotEmpty()&&collection.isMusic){
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(enabled=!downloading,onClick={scope.launch{
     downloading=true;downloadMessage="Queueing original audio downloads…"
     val count=queueMusicDownloads(context,tracks,false)
     downloadMessage="Queued $count of ${tracks.size} tracks."
     downloading=false
    }},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Download album")}
    OutlinedButton(enabled=!downloading,onClick={scope.launch{
     downloading=true;downloadMessage="Queueing MP3 conversions…"
     val count=queueMusicDownloads(context,tracks,true)
     downloadMessage="Queued $count of ${tracks.size} MP3 downloads."
     downloading=false
    }}){Text("MP3")}
   }
   TextButton(onClick={tracks.forEach(enqueue);downloadMessage="Added ${tracks.size} tracks to the queue."}){Text("Queue all",color=magenta)}
   if(downloadMessage.isNotBlank())Text(downloadMessage,color=muted,fontSize=10.sp)
  } else if(tracks.isNotEmpty()){
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
    Button(enabled=!downloading,onClick={scope.launch{
     downloading=true;downloadMessage="Queueing MP4 video downloads…"
     val count=queueVideoDownloads(context,tracks)
     downloadMessage="Queued $count of ${tracks.size} MP4 downloads."
     downloading=false
    }},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Download MP4")}
    OutlinedButton(enabled=!downloading,onClick={scope.launch{
     downloading=true;downloadMessage="Extracting MP3 audio…"
     val count=queueMusicDownloads(context,tracks,true)
     downloadMessage="Queued $count of ${tracks.size} MP3 downloads."
     downloading=false
    }}){Text("Download MP3")}
   }
   TextButton(onClick={tracks.forEach(enqueue);downloadMessage="Added ${tracks.size} videos to the queue."}){Text("Queue all",color=magenta)}
   if(downloadMessage.isNotBlank())Text(downloadMessage,color=muted,fontSize=10.sp)
  }
  if(tracks.isNotEmpty()){
   TextButton(enabled=libraryMessage.isBlank(),onClick={
    val prefs=context.getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)
    tracks.forEach(store)
    val sourceKey=(if(collection.isMusic)"youtube-music:" else "youtube-playlist:")+collection.collectionId
    PlaylistStore.importCollection(prefs,collection.title,sourceKey,tracks.map{it.videoId})
    libraryMessage="Imported "+tracks.size+" items into Library as \""+collection.title+"\"."
   }){Text("Save to Library",color=magenta)}
   if(libraryMessage.isNotBlank())Text(libraryMessage,color=muted,fontSize=10.sp)
  }
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
   items(tracks,key={it.videoId}){track->
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).clickable{play(track)}.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
     YouTubeThumbnail(track.thumbnailUrl,track.videoId,Modifier.size(42.dp),placeholder=if(collection.isMusic)"♫" else "▶")
     Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(track.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(track.creator+" · "+track.duration,color=muted,fontSize=10.sp,maxLines=1)}
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
 var refresh by remember{mutableIntStateOf(0)}
 LaunchedEffect(channels.map{it.id},refresh) {
  loading=true
  try {
   uploads=withContext(Dispatchers.IO){channels.flatMap{channel->runCatching{LiveYouTubeSearch.latestUploads(channel.id)}.getOrDefault(emptyList())}.distinctBy{it.videoId}.take(60)}
   status=if(uploads.isEmpty())"No recent public uploads found." else "${uploads.size} recent uploads"
  } catch(e:Exception){status=playbackErrorMessage(e,"Could not refresh subscriptions.")}
  finally{loading=false}
 }
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{Text("Subscriptions",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))}
    TextButton(enabled=!loading,onClick={refresh++}){Text("Refresh",color=magenta)}
   }
  }
  if(channels.isEmpty())item{Text("No subscriptions yet. Search for a channel in Discover and subscribe to it.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(16.dp))}
  items(channels,key={it.id}){channel->
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
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
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).clickable{play(video)}.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
    YouTubeThumbnail(video.thumbnailUrl,video.videoId,Modifier.size(45.dp),placeholder="▶")
    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(video.title,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2);Text(video.creator+" · "+video.duration,color=muted,fontSize=10.sp,maxLines=1)}
    Text("Play",color=magenta,fontSize=11.sp)
   }
  }
 }
}

@Composable private fun DownloadsScreen(ink:Color,muted:Color,surface:Color,play:(OfflineMedia)->Unit,playAll:(List<OfflineMedia>)->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 var downloads by remember { mutableStateOf<List<OfflineMedia>>(emptyList()) }
 var workInfos by remember { mutableStateOf<List<androidx.work.WorkInfo>>(emptyList()) }
 var storageBytes by remember{mutableLongStateOf(0L)}
 var resumeMessage by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 LaunchedEffect(context) {
  while (true) {
   val snapshot=withContext(Dispatchers.IO){
    Triple(OfflineDownloads.list(context),runCatching{OfflineDownloads.workInfos(context)}.getOrDefault(emptyList()),OfflineDownloads.storageBytes(context))
   }
   downloads=snapshot.first
   workInfos=snapshot.second
   storageBytes=snapshot.third
   kotlinx.coroutines.delay(1200)
  }
 }
 val working=workInfos.mapNotNull{work->work.tags.firstOrNull{tag->tag.startsWith("${OfflineDownloads.WORK_TAG}:")}?.substringAfter(":")?.let{id->id to work}}.toMap()
  LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item {
   Text("Offline downloads",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Text("${downloads.size} items · ${android.text.format.Formatter.formatShortFileSize(context,storageBytes)} used",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))
   Text("Files stay in Rush's private app storage. Download only content you are permitted to keep offline.",color=muted,fontSize=10.sp,modifier=Modifier.padding(top=7.dp))
   if(resumeMessage.isNotBlank())Text(resumeMessage,color=muted,fontSize=10.sp,modifier=Modifier.padding(top=4.dp))
  }
  val readyAudio=downloads.filter{it.complete&&it.isAudio}
  val readyVideo=downloads.filter{it.complete&&!it.isAudio}
  if(readyAudio.isNotEmpty())item{TextButton(onClick={playAll(readyAudio)}){Text("Play downloaded music · ${readyAudio.size}",color=magenta)}}
  if(readyVideo.isNotEmpty())item{TextButton(onClick={playAll(readyVideo)}){Text("Play downloaded videos · ${readyVideo.size}",color=magenta)}}
  if(downloads.isEmpty()) item {
   Text("Your offline library is empty. Open a video or music player and choose Download offline.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(18.dp))
  }
  items(downloads,key={it.id}) { media ->
   val work=working[media.id]
   val progress=work?.progress?.getInt("progress",0)?:0
   val downloadedBytes=work?.progress?.getLong("bytes",0L)?:0L
   val converting=work?.progress?.getString("stage")=="Converting to MP3"
   val active=work?.state==androidx.work.WorkInfo.State.RUNNING || work?.state==androidx.work.WorkInfo.State.ENQUEUED || work?.state==androidx.work.WorkInfo.State.BLOCKED
   Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text(media.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
    Text(media.creator+" · "+(if(media.isAudio)"Audio" else "Video")+" · "+media.quality+" · ."+media.extension,color=muted,fontSize=10.sp,maxLines=2)
    Text(when {
     media.complete -> "Ready offline · ${android.text.format.Formatter.formatShortFileSize(context,media.file.length())}"
     work?.state==androidx.work.WorkInfo.State.RUNNING -> work.progress.getString("stage") ?: "Downloading · $progress% · ${android.text.format.Formatter.formatShortFileSize(context,downloadedBytes)}"
     work?.state==androidx.work.WorkInfo.State.ENQUEUED || work?.state==androidx.work.WorkInfo.State.BLOCKED -> "Queued"
     work?.state==androidx.work.WorkInfo.State.FAILED || work?.state==androidx.work.WorkInfo.State.CANCELLED -> "Interrupted · resume to continue"
     else -> "Preparing download"
    },color=if(media.complete)mint else muted,fontSize=11.sp)
    if(active){
     if(converting||progress==0)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
     else LinearProgressIndicator(progress={progress/100f},modifier=Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     if(media.complete) Button(onClick={play(media)},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Play offline")}
     else if(active) OutlinedButton(onClick={OfflineDownloads.cancel(context,media.id)}){Text("Cancel")}
     else Button(onClick={scope.launch{resumeMessage="Refreshing stream and resuming download…";try{OfflineDownloads.resume(context,media);resumeMessage="Download resumed."}catch(e:Exception){resumeMessage=e.localizedMessage?:"Could not refresh this download. Try downloading it again."}}},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Resume")}
     TextButton(onClick={scope.launch{OfflineDownloads.delete(context,media)}}){Text("Delete",color=muted)}
    }
   }
  }
 }
}

@Composable private fun Library(saved:Set<String>,history:List<String>,metadata:Map<String,Pick>,prefs:android.content.SharedPreferences,queueIds:List<String>,ink:Color,muted:Color,surface:Color,play:(String)->Unit,remove:(String)->Unit,playQueue:(List<Pick>)->Unit,updateQueue:(List<String>)->Unit,clearHistory:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 val all=metadata
 val kept=saved.mapNotNull{all[it]};val recent=history.distinct().mapNotNull{all[it]}
 var playlists by remember{mutableStateOf(PlaylistStore.load(prefs))}
 var showCreate by remember{mutableStateOf(false)}
 var playlistName by remember{mutableStateOf("")}
 var playlistMessage by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Shelf("Up next","Persistent queue",ink)
    if(queueIds.isNotEmpty())TextButton(onClick={updateQueue(emptyList())}){Text("Clear",color=muted,fontSize=10.sp)}
   }
  }
  val queued=queueIds.mapNotNull{all[it]}
  if(queued.isNotEmpty()){
   item{TextButton(onClick={playQueue(queued)}){Text("Play queue · ${queued.size} items",color=magenta)}}
   itemsIndexed(queued){index,track->
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(horizontal=10.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
     Text("${index+1}.",color=muted,fontSize=11.sp)
     Text(track.title,color=ink,fontSize=11.sp,modifier=Modifier.weight(1f).padding(horizontal=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
     Text("↑",color=if(index>0)muted else muted.copy(alpha=.3f),modifier=Modifier.clickable(enabled=index>0){updateQueue(QueueStore.move(prefs,index,index-1))}.padding(5.dp))
     Text("↓",color=if(index<queued.lastIndex)muted else muted.copy(alpha=.3f),modifier=Modifier.clickable(enabled=index<queued.lastIndex){updateQueue(QueueStore.move(prefs,index,index+1))}.padding(5.dp))
     Text("▶",color=magenta,modifier=Modifier.clickable{play(track.id)}.padding(5.dp))
     Text("×",color=muted,modifier=Modifier.clickable{updateQueue(queueIds.filterIndexed{position,_->position!=index})}.padding(5.dp))
    }
   }
  }else item{Text("Add tracks from Discover using the queue icon to build an Up next list.",color=muted,fontSize=10.sp)}
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Shelf("Playlists","Your collections",ink)
    TextButton(onClick={showCreate=true}){Text("＋ New",color=magenta)}
   }
  }
  if(playlists.isEmpty())item{Text("Create a playlist, then add videos or music from Discover.",color=muted,fontSize=11.sp)}
  items(playlists,key={it.id}){playlist->
   Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    val tracks=playlist.itemIds.mapNotNull{all[it]}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text(playlist.name,color=ink,fontSize=14.sp,fontWeight=FontWeight.Bold);Text("${playlist.itemIds.size} items",color=muted,fontSize=10.sp)}
     TextButton(onClick={
      scope.launch {
       playlistMessage="Resolving playlist streams…"
       var queued=0
       for(track in tracks){
        try{
         val resolved=withContext(Dispatchers.IO){resolvePlaybackMedia(track.id,track.music)}
         withContext(Dispatchers.IO){OfflineDownloads.enqueue(context=context,id=track.id,title=track.title,creator=track.creator,isAudio=track.music,media=resolved)}
         queued++
        }catch(_:Exception){}
       }
       playlistMessage="Queued $queued of ${playlist.itemIds.size} playlist downloads."
      }
     }){Text("Download all",color=magenta,fontSize=10.sp)}
     if(tracks.any{it.music})TextButton(onClick={
      scope.launch {
       playlistMessage="Preparing MP3 album downloads…"
       var queuedMp3=0
       for(track in tracks.filter{it.music}){
        try{
         val resolved=withContext(Dispatchers.IO){resolvePlaybackMedia(track.id,true)}
         withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,track.id,track.title,track.creator,true,resolved,transcodeToMp3=true)}
         queuedMp3++
        }catch(_:Exception){}
       }
       playlistMessage="Queued $queuedMp3 MP3 conversion downloads."
      }
     }){Text("MP3 audio",color=magenta,fontSize=10.sp)}
     if(tracks.any{!it.music})TextButton(onClick={scope.launch{
      playlistMessage="Extracting MP3 audio from video tracks…"
      val queuedMp3=queueLocalPickMp3Downloads(context,tracks.filter{!it.music})
      playlistMessage="Queued $queuedMp3 MP3 audio extractions."
     }}){Text("Extract MP3",color=magenta,fontSize=10.sp)}
     if(tracks.isNotEmpty())TextButton(onClick={playQueue(tracks)}){Text("Play all",color=magenta,fontSize=10.sp)}
     TextButton(onClick={playlists=PlaylistStore.delete(prefs,playlist.id)}){Text("Delete",color=muted,fontSize=10.sp)}
    }
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
  if(playlistMessage.isNotBlank())item{Text(playlistMessage,color=muted,fontSize=10.sp)}
  item{Shelf("Saved items","Bookmarks",ink)}
  if(kept.isEmpty())item{Text("Saved videos and music will appear here.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(18.dp))}
  items(kept){pick->CardRow(pick,ink,muted,surface,saved,{play(pick.id)},{remove(pick.id)})}
  item{
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Shelf("Recently played","History",ink)
    if(recent.isNotEmpty())TextButton(onClick=clearHistory){Text("Clear",color=muted,fontSize=10.sp)}
   }
  }
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
@Composable private fun RushPlayer(item:Pick,light:Boolean,back:()->Unit,queue:List<Pick>,onTrackChanged:(String)->Unit,isInPipMode:Boolean=false){
 val context=androidx.compose.ui.platform.LocalContext.current;val scope=rememberCoroutineScope()
 val playerPrefs=remember(context){context.getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)}
 var playingPick by remember(item.id){mutableStateOf(item)}
 val localFileAvailable=playingPick.localPath?.let{java.io.File(it).isFile}==true
 val requestNotificationPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){ }
 var controller by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
 val controllerFuture = remember(context) {
  val token = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, RushPlaybackService::class.java))
  androidx.media3.session.MediaController.Builder(context, token).buildAsync()
 }
 DisposableEffect(controllerFuture) {
  controllerFuture.addListener({ controller = runCatching { controllerFuture.get() }.getOrNull() }, context.mainExecutor)
  onDispose { androidx.media3.session.MediaController.releaseFuture(controllerFuture) }
 }
 var url by remember(item.id){mutableStateOf((item.sourceId?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}?:item.id.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}).orEmpty())};var audio by remember(item.id){mutableStateOf(item.music)}
 var quality by remember(item.id){mutableIntStateOf(playerPrefs.getInt(if(item.music)"audio_quality" else "video_quality",0))}
 var mp3Format by remember(item.id){mutableStateOf(playerPrefs.getBoolean("download_mp3",false))}
 var mp4Format by remember(item.id){mutableStateOf(playerPrefs.getBoolean("download_mp4",false))}
 var speed by remember(item.id){mutableFloatStateOf(playerPrefs.getFloat("playback_speed",1f))}
 val audioManager=remember(context){context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager}
 val maxVolume=remember(audioManager){audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)}
 var volumeLevel by remember(item.id){mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()/maxVolume)}
 val playerActivity=context.activityOrNull()
 val originalBrightness=remember(playerActivity){playerActivity?.window?.attributes?.screenBrightness?:-1f}
 val systemBrightness=remember(context){runCatching{android.provider.Settings.System.getInt(context.contentResolver,android.provider.Settings.System.SCREEN_BRIGHTNESS,128)/255f}.getOrDefault(0.5f)}
 var brightnessLevel by remember(item.id){mutableFloatStateOf(originalBrightness.takeIf{it>=0f}?:systemBrightness.coerceIn(0.01f,1f))}
 var landscape by remember(item.id){mutableStateOf(false)}
 val screenHeightDp=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
 val videoPlayerHeight=if(audio)96.dp else if(landscape)(screenHeightDp*0.72f).dp else 220.dp
 DisposableEffect(playerActivity){
  onDispose{
   playerActivity?.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
   playerActivity?.window?.let{window->
    window.attributes=window.attributes.apply{screenBrightness=originalBrightness}
   }
  }
 }
 var captionsEnabled by remember(item.id){mutableStateOf(playerPrefs.getBoolean("captions_enabled",true))}
 var status by remember(item.id){mutableStateOf(if(localFileAvailable)"Downloaded file ready." else "Paste a YouTube link or video ID to play.")}
 var isPlaying by remember(item.id){mutableStateOf(false)}
 var playingTitle by remember(item.id){mutableStateOf(item.title)}
 var playingCreator by remember(item.id){mutableStateOf(item.creator)}
 var isBuffering by remember(item.id){mutableStateOf(false)}
 val latestOnTrackChanged by rememberUpdatedState(onTrackChanged)
 val latestQueue by rememberUpdatedState(queue)
 val latestPlayingPick by rememberUpdatedState(playingPick)
 SideEffect{
  (context.activityOrNull() as? MainActivity)?.setPipEligible(controller?.currentMediaItem!=null&&!audio)
 }
 DisposableEffect(controller){
  val listener=object:androidx.media3.common.Player.Listener{
   override fun onMediaItemTransition(mediaItem:androidx.media3.common.MediaItem?,reason:Int){
    val mediaId=mediaItem?.mediaId.orEmpty()
    val nextPick=latestQueue.firstOrNull{it.id==mediaId}?:if(mediaId==item.id)item else null
    if(nextPick!=null){
     val trackChanged=nextPick.id!=latestPlayingPick.id
     val mediaTypeChanged=nextPick.music!=latestPlayingPick.music
     playingPick=nextPick
     playingTitle=nextPick.title
     playingCreator=nextPick.creator
     if(trackChanged){
      audio=nextPick.music
      url=(nextPick.sourceId?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}?:nextPick.id.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}).orEmpty()
      status=if(nextPick.localPath?.let{java.io.File(it).isFile}==true)"Downloaded file ready." else "Playing "+nextPick.title
      if(mediaTypeChanged)quality=playerPrefs.getInt(if(nextPick.music)"audio_quality" else "video_quality",0)
     }
    }else{
     playingTitle=mediaItem?.mediaMetadata?.title?.toString()?.ifBlank{latestPlayingPick.title}?:latestPlayingPick.title
     playingCreator=mediaItem?.mediaMetadata?.artist?.toString()?.ifBlank{latestPlayingPick.creator}?:latestPlayingPick.creator
    }
    isPlaying=controller?.isPlaying==true
    mediaId.takeIf{it.isNotBlank()}?.let(latestOnTrackChanged)
   }
   override fun onIsPlayingChanged(isPlayingNow:Boolean){isPlaying=isPlayingNow}
   override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){
    val height=controller?.videoSize?.height?:0
    status=if(playWhenReady)if(height>0)"Playing · ${height}p" else "Playing" else "Paused"
   }
   override fun onPlaybackStateChanged(playbackState:Int){
    isBuffering=playbackState==androidx.media3.common.Player.STATE_BUFFERING
    if(playbackState==androidx.media3.common.Player.STATE_READY&&controller?.isPlaying!=true)status="Ready to play"
   }
   override fun onVideoSizeChanged(videoSize:androidx.media3.common.VideoSize){
    if(videoSize.height>0){
     status=if(controller?.isPlaying==true)"Playing · ${videoSize.height}p" else "${videoSize.height}p"
    }
    (context.activityOrNull() as? MainActivity)?.updatePipAspectRatio(videoSize.width,videoSize.height)
   }
   override fun onPlayerError(error:androidx.media3.common.PlaybackException){
    isBuffering=false
    status="Playback interrupted. Refreshing the stream…"
   }
  }
  controller?.addListener(listener)
  isPlaying=controller?.isPlaying==true
  onDispose{controller?.removeListener(listener);isPlaying=false}
 }
 var autoStartAttempted by remember(item.id){mutableStateOf(false)}
 LaunchedEffect(controller,playingPick.id,queue.isEmpty(),localFileAvailable){
  val activePlayer=controller?:return@LaunchedEffect
  if(queue.isNotEmpty()){autoStartAttempted=true;return@LaunchedEffect}
  if(autoStartAttempted)return@LaunchedEffect
  autoStartAttempted=true
  status="Loading media…"
  try{
   var resolvedQuality=""
   val mediaItem=withContext(Dispatchers.IO){
    val localFile=playingPick.localPath?.let{java.io.File(it)}?.takeIf{it.isFile}
    if(localFile!=null){
     androidx.media3.common.MediaItem.Builder().setMediaId(playingPick.id).setUri(localFile.toURI().toString())
      .setMediaMetadata(playingPick.toMediaMetadata()).build()
    }else{
     val source=url.ifBlank{playingPick.sourceId?:playingPick.id}
     val resolved=resolvePlaybackMedia(source,audio,if(audio)null else quality.takeIf{it>0},if(audio)quality.takeIf{it>0} else null,!audio&&mp4Format,!audio&&!mp4Format,!audio&&!mp4Format)
     resolvedQuality=resolved.qualityLabel
     val subtitles=if(audio)emptyList() else resolved.subtitles
     val configs=subtitles.map{subtitle->
      androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
       .setMimeType(subtitle.mimeType).setLanguage(subtitle.languageTag)
       .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
       .setLabel(subtitle.label).build()
     }
     androidx.media3.common.MediaItem.Builder().setMediaId(playingPick.id).setUri(resolved.url).setMimeType(resolved.mimeType)
      .setMediaMetadata(playingPick.toMediaMetadata(source,audio,quality,mp4Format))
      .setSubtitleConfigurations(configs).build()
    }
   }
   activePlayer.setMediaItem(mediaItem)
   val resumeAt=PlaybackProgress.resumePositionMs(context,playingPick.id)
   if(resumeAt>0)activePlayer.seekTo(resumeAt)
   activePlayer.prepare()
   activePlayer.play()
   status=if(localFileAvailable)"Playing offline" else "Playing · $resolvedQuality"
  }catch(e:Exception){
   status=playbackErrorMessage(e,"Could not start playback automatically. Use Load and play to retry.")
  }
 }
 var shuffleEnabled by remember(item.id){mutableStateOf(playerPrefs.getBoolean("shuffle_enabled",false))}
 var repeatMode by remember(item.id){mutableIntStateOf(playerPrefs.getInt("repeat_mode",androidx.media3.common.Player.REPEAT_MODE_OFF))}
 LaunchedEffect(controller,shuffleEnabled,repeatMode,captionsEnabled,quality,audio){
  controller?.let{player->
   player.setShuffleModeEnabled(shuffleEnabled)
   player.setRepeatMode(repeatMode)
   player.trackSelectionParameters=player.trackSelectionParameters.buildUpon()
    .setPreferredTextLanguage("en")
    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,!captionsEnabled)
    .setMinVideoSize(1,if(!audio&&quality>0)quality else 0)
    .setMaxVideoSize(Int.MAX_VALUE,if(!audio&&quality>0)quality else Int.MAX_VALUE)
    .setMaxAudioBitrate(if(audio&&quality>0)quality*1000 else Int.MAX_VALUE)
    .build()
  }
 }
 LaunchedEffect(controller,quality,audio,mp4Format,playingPick.id){
  val player=controller?:return@LaunchedEffect
  val current=player.currentMediaItem?:return@LaunchedEffect
  val currentUri=current.localConfiguration?.uri?:return@LaunchedEffect
  if(currentUri.scheme=="file"||currentUri.scheme=="content"||currentUri.scheme=="android.resource")return@LaunchedEffect

  val options=current.mediaMetadata.extras?:return@LaunchedEffect
  val source=options.getString(PlaybackMetadata.SOURCE).orEmpty()
  if(source.isBlank())return@LaunchedEffect
  val currentAudio=options.getBoolean(PlaybackMetadata.AUDIO_ONLY,false)
  val currentQuality=options.getInt(PlaybackMetadata.QUALITY,0)
  val currentMp4=options.getBoolean(PlaybackMetadata.PREFER_MP4,false)
  val mime=current.localConfiguration?.mimeType.orEmpty()
  val isAdaptive=mime.equals("application/dash+xml",true)||
   mime.equals("application/x-mpegURL",true)||
   currentUri.scheme=="rush-sabr"
  val sourceSelectionChanged=currentAudio!=audio||currentMp4!=mp4Format
  val progressiveQualityChanged=!isAdaptive&&currentQuality!=quality
  if(!sourceSelectionChanged&&!progressiveQualityChanged)return@LaunchedEffect

  val mediaId=current.mediaId
  val startPosition=player.currentPosition.coerceAtLeast(0L)
  status=if(sourceSelectionChanged)"Changing playback format…" else "Changing video quality…"
  try{
   val resolved=withContext(Dispatchers.IO){
    resolvePlaybackMedia(
     source,audio,
     if(audio)null else quality.takeIf{it>0},
     if(audio)quality.takeIf{it>0} else null,
     !audio&&mp4Format,
     !audio&&!mp4Format,
     !audio&&!mp4Format
    )
   }
   val subtitleConfigs=if(audio)emptyList() else resolved.subtitles.map{subtitle->
    androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
     .setMimeType(subtitle.mimeType)
     .setLanguage(subtitle.languageTag)
       .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
     .setLabel(subtitle.label)
     .build()
   }
   if(player.currentMediaItem?.mediaId!=mediaId)return@LaunchedEffect
   val refreshed=androidx.media3.common.MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(resolved.url)
    .setMimeType(resolved.mimeType)
    .setMediaMetadata(playingPick.toMediaMetadata(source,audio,quality,mp4Format))
    .setSubtitleConfigurations(subtitleConfigs)
    .build()
   val index=player.currentMediaItemIndex
   player.replaceMediaItem(index,refreshed)
   player.seekTo(index,startPosition)
   player.prepare()
   player.play()
   status="Playing · ${resolved.qualityLabel}"
  }catch(cancelled:kotlinx.coroutines.CancellationException){
   throw cancelled
  }catch(e:Exception){
   status=playbackErrorMessage(e,"Could not change playback quality.")
  }
 }
 LaunchedEffect(controller,speed){
  val player=controller?:return@LaunchedEffect
  val startSpeed=player.playbackParameters.speed.coerceIn(0.25f,4f)
  val targetSpeed=speed.coerceIn(0.25f,4f)
  val steps=5
  if(kotlin.math.abs(targetSpeed-startSpeed)<0.01f){
   player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(targetSpeed))
  }else{
   repeat(steps){step->
    val progress=(step+1)/steps.toFloat()
    val nextSpeed=startSpeed+(targetSpeed-startSpeed)*progress
    player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(nextSpeed))
    kotlinx.coroutines.delay(60)
   }
  }
 }
 LaunchedEffect(controller,queue.map{it.id}.joinToString("|")){
  val activeController=controller
  if(activeController!=null&&queue.isNotEmpty()){
   status="Preparing playlist queue…"
   var skipped=0
   try{
    suspend fun prepareQueueItem(queued:Pick):androidx.media3.common.MediaItem=withContext(Dispatchers.IO){
     val localFile=queued.localPath?.let{java.io.File(it)}?.takeIf{it.isFile}
     val queuedQuality=playerPrefs.getInt(if(queued.music)"audio_quality" else "video_quality",0)
     val queuedMp4=!queued.music&&playerPrefs.getBoolean("download_mp4",false)
     val queuedSource=queued.sourceId?:queued.id
     val resolved=if(localFile==null)resolvePlaybackMedia(
      queuedSource,queued.music,
      if(queued.music)null else queuedQuality.takeIf{it>0},
      if(queued.music)queuedQuality.takeIf{it>0} else null,
      queuedMp4,!queued.music&&!queuedMp4,!queued.music&&!queuedMp4
     ) else null
     val uri=localFile?.toURI()?.toString()?:resolved!!.url
     val subtitleConfigs=resolved?.subtitles.orEmpty().map{subtitle->
      androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
       .setMimeType(subtitle.mimeType)
       .setLanguage(subtitle.languageTag)
       .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
       
       .setLabel(subtitle.label)
       .build()
     }
     androidx.media3.common.MediaItem.Builder().setMediaId(queued.id).setUri(uri).apply{resolved?.mimeType?.let{setMimeType(it)}}.setMediaMetadata(queued.toMediaMetadata(queuedSource,queued.music,queuedQuality,queuedMp4)).setSubtitleConfigurations(subtitleConfigs).build()
    }
    val firstItems=ArrayList<androidx.media3.common.MediaItem>()
    var nextQueueIndex=0
    while(nextQueueIndex<queue.size&&firstItems.size<1){
     val queued=queue[nextQueueIndex++]
     try{firstItems+=prepareQueueItem(queued)}
     catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
     catch(_:Exception){skipped++}
    }
    if(firstItems.isEmpty()){
     status="No tracks in this queue could be resolved."
     return@LaunchedEffect
    }
    activeController.setMediaItems(firstItems)
    val savedPosition=PlaybackProgress.resumePositionMs(context,firstItems.first().mediaId)
    if(savedPosition>0)activeController.seekTo(0,savedPosition)
    activeController.prepare()
    activeController.play()
    status="Playing queue · preparing ${queue.size-nextQueueIndex} more"
    for(queued in queue.drop(nextQueueIndex)){
     try{activeController.addMediaItem(prepareQueueItem(queued))}
     catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
     catch(_:Exception){skipped++}
    }
    status="Queue ready · ${queue.size-skipped} of ${queue.size} tracks"+if(skipped>0)" · $skipped unavailable" else ""
   }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
   catch(e:Exception){status=playbackErrorMessage(e,"Could not prepare this playlist queue.")}
  }
 }
 val ink=if(light)Color(0xFF241B32)else paper;val muted=if(light)Color(0xFF70677D)else mutedDark;val surface=if(light)Color.White else panelDark

 val recoveryMessage=PlaybackRecoveryStatus.current.value?.takeIf{it.mediaId==playingPick.id}?.message
 if(isInPipMode){
  androidx.compose.ui.viewinterop.AndroidView(
   factory={ctx->androidx.media3.ui.PlayerView(ctx).prepareForPlayback(controller,isPlaying&&!audio)},
   update={it.prepareForPlayback(controller,isPlaying&&!audio)},
   modifier=Modifier.fillMaxSize()
  )
 }else Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Text("← Back",color=muted,modifier=Modifier.clickable{back()}.padding(8.dp))
   Text(if(audio)"Music player"else"Video player",color=mint)
   if(!audio&&controller?.currentMediaItem!=null&&android.os.Build.VERSION.SDK_INT>=26)TextButton(onClick={(context.activityOrNull() as? MainActivity)?.enterPlayerPip()}){Text("Picture-in-Picture",color=magenta)}
  }
  Spacer(Modifier.height(8.dp))
  androidx.compose.ui.viewinterop.AndroidView(factory={ctx->androidx.media3.ui.PlayerView(ctx).prepareForPlayback(controller,isPlaying&&!audio)},update={it.prepareForPlayback(controller,isPlaying&&!audio)},modifier=Modifier.fillMaxWidth().height(videoPlayerHeight))
  Text(if(isBuffering)"Buffering…" else recoveryMessage?:status,color=muted,fontSize=11.sp)
  Text(playingTitle,color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(playingCreator,color=muted)
  if(!localFileAvailable) {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=audio,onClick={audio=true;quality=playerPrefs.getInt("audio_quality",0)},label={Text("Audio only")});FilterChip(selected=!audio,onClick={audio=false;quality=playerPrefs.getInt("video_quality",0)},label={Text("Video")})}
   if(!audio) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0 to "Auto",2160 to "2160p",1440 to "1440p",1080 to "1080p",720 to "720p",480 to "480p",360 to "360p").forEach{(height,label)->FilterChip(selected=quality==height,onClick={quality=height;playerPrefs.edit().putInt(if(audio)"audio_quality" else "video_quality",height).apply()},label={Text(label)})}} else Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0 to "Best",320 to "320 kbps",256 to "256 kbps",192 to "192 kbps",160 to "160 kbps",96 to "96 kbps").forEach{(bitrate,label)->FilterChip(selected=quality==bitrate,onClick={quality=bitrate;playerPrefs.edit().putInt("audio_quality",bitrate).apply()},label={Text(label)})}}
   if(!audio)Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected=mp4Format,onClick={mp4Format=true;playerPrefs.edit().putBoolean("download_mp4",true).apply()},label={Text("MP4")});FilterChip(selected=!mp4Format,onClick={mp4Format=false;playerPrefs.edit().putBoolean("download_mp4",false).apply()},label={Text("Original format")})}
   if(audio)Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected=!mp3Format,onClick={mp3Format=false;playerPrefs.edit().putBoolean("download_mp3",false).apply()},label={Text("Original format")});FilterChip(selected=mp3Format,onClick={mp3Format=true;playerPrefs.edit().putBoolean("download_mp3",true).apply()},label={Text("MP3 · 192 kbps")})}
   BasicTextField(url,{url=it},modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink),singleLine=true,decorationBox={inner->if(url.isEmpty())Text("YouTube URL or video ID",color=muted);inner()})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){
   Text("Speed",color=muted,fontSize=10.sp)
   listOf(0.75f,1f,1.25f,1.5f,2f).forEach{value->
    FilterChip(selected=speed==value,onClick={speed=value;playerPrefs.edit().putFloat("playback_speed",value).apply()},label={Text("${value}x",fontSize=10.sp)})
   }
  }
  Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Text("Volume",color=muted,fontSize=11.sp,modifier=Modifier.width(66.dp))
    Slider(value=volumeLevel,onValueChange={value->
     volumeLevel=value
     audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,(value*maxVolume).toInt().coerceIn(0,maxVolume),0)
    },modifier=Modifier.weight(1f))
    Text("${(volumeLevel*100).toInt()}%",color=muted,fontSize=10.sp,modifier=Modifier.width(38.dp))
   }
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Text("Brightness",color=muted,fontSize=11.sp,modifier=Modifier.width(66.dp))
    Slider(value=brightnessLevel.coerceIn(0.01f,1f),onValueChange={value->
     brightnessLevel=value
     playerActivity?.window?.let{window->
      window.attributes=window.attributes.apply{screenBrightness=value}
     }
    },modifier=Modifier.weight(1f))
    Text("${(brightnessLevel*100).toInt()}%",color=muted,fontSize=10.sp,modifier=Modifier.width(38.dp))
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
   FilterChip(selected=landscape,onClick={
    landscape=!landscape
    playerActivity?.requestedOrientation=if(landscape)android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
   },label={Text(if(landscape)"Landscape" else "Portrait")})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
   FilterChip(selected=captionsEnabled,onClick={
    captionsEnabled=!captionsEnabled
    playerPrefs.edit().putBoolean("captions_enabled",captionsEnabled).apply()
    controller?.let{it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setPreferredTextLanguage("en")
    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,!captionsEnabled).build()}
   },label={Text(if(captionsEnabled)"CC · English" else "CC off")})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
   FilterChip(selected=shuffleEnabled,onClick={shuffleEnabled=!shuffleEnabled;playerPrefs.edit().putBoolean("shuffle_enabled",shuffleEnabled).apply();controller?.setShuffleModeEnabled(shuffleEnabled)},label={Text("Shuffle")})
   FilterChip(selected=repeatMode!=androidx.media3.common.Player.REPEAT_MODE_OFF,onClick={
    repeatMode=when(repeatMode){androidx.media3.common.Player.REPEAT_MODE_OFF->androidx.media3.common.Player.REPEAT_MODE_ALL;androidx.media3.common.Player.REPEAT_MODE_ALL->androidx.media3.common.Player.REPEAT_MODE_ONE;else->androidx.media3.common.Player.REPEAT_MODE_OFF}
    playerPrefs.edit().putInt("repeat_mode",repeatMode).apply()
    controller?.setRepeatMode(repeatMode)
   },label={Text(when(repeatMode){androidx.media3.common.Player.REPEAT_MODE_ALL->"Repeat all";androidx.media3.common.Player.REPEAT_MODE_ONE->"Repeat one";else->"Repeat off"})})
  }
  if(queue.isNotEmpty())Text("${queue.size} items in queue",color=muted,fontSize=10.sp)
  Button(onClick={
   PlaybackRecoveryStatus.current.value=null
   val activePlayer=controller
   if(activePlayer==null){status="Connecting to playback service…"}
   else scope.launch{
    status="Resolving stream…"
    try{
     var resolvedQuality=""
     val mediaItem=if(localFileAvailable){
      androidx.media3.common.MediaItem.Builder().setMediaId(playingPick.id).setUri(java.io.File(playingPick.localPath!!).toURI().toString()).setMediaMetadata(playingPick.toMediaMetadata()).build()
     }else{
      val resolved=withContext(Dispatchers.IO){resolvePlaybackMedia(url,audio,if(audio)null else quality.takeIf{it>0},if(audio)quality.takeIf{it>0} else null,!audio&&mp4Format,!audio&&!mp4Format,!audio&&!mp4Format,forceRefresh=true)}
      resolvedQuality=resolved.qualityLabel
      val captions=if(audio)emptyList() else resolved.subtitles
      val subtitleConfigs=captions.map{subtitle->
       androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
        .setMimeType(subtitle.mimeType)
        .setLanguage(subtitle.languageTag)
       .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
        
        .setLabel(subtitle.label)
        .build()
      }
      androidx.media3.common.MediaItem.Builder().setMediaId(playingPick.id).setUri(resolved.url).setMimeType(resolved.mimeType).setMediaMetadata(playingPick.toMediaMetadata(url,audio,quality,mp4Format)).setSubtitleConfigurations(subtitleConfigs).build()
     }
     activePlayer.setMediaItem(mediaItem)
     val savedPosition=PlaybackProgress.resumePositionMs(context,playingPick.id)
     if(savedPosition>0)activePlayer.seekTo(savedPosition)
     activePlayer.prepare();activePlayer.play();status=if(localFileAvailable)"Playing offline" else "Playing · $resolvedQuality"
    }catch(e:Exception){status=playbackErrorMessage(e,"Unable to load this stream.")}
   }
  },enabled=controller!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text(if(localFileAvailable)"Play offline" else "Load and play",color=Color.White)}
  Button(
   onClick={if(controller?.isPlaying==true)controller?.pause() else controller?.play()},
   enabled=controller?.currentMediaItem!=null,
   modifier=Modifier.fillMaxWidth(),
   colors=ButtonDefaults.buttonColors(containerColor=raised)
  ){Text(if(isPlaying)"Pause playback" else "Play playback",color=ink)}
  if(!localFileAvailable) Button(onClick={
   if(android.os.Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
    requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
   }
   scope.launch{
   status="Preparing offline download…"
   try{
    val media=withContext(Dispatchers.IO){resolvePlaybackMedia(url,audio,if(audio)null else quality.takeIf{it>0},if(audio)quality.takeIf{it>0} else null,!audio&&mp4Format)}
    withContext(Dispatchers.IO){OfflineDownloads.enqueue(context,playingPick.id,playingPick.title,playingPick.creator,audio,media,if(audio)null else quality.takeIf{it>0},if(audio)quality.takeIf{it>0} else null,audio&&mp3Format,!audio&&mp4Format)}
    status="Download queued · ${media.qualityLabel} · .${media.extension}"
   }catch(e:Exception){status=playbackErrorMessage(e,"Could not start download.")}
  }},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=raised)){Text(when{audio&&mp3Format->"Download MP3";!audio&&mp4Format->"Download MP4";else->"Download offline"},color=ink)}

  Text("Use offline downloads only where permitted. Formats and quality depend on the source.",color=muted,fontSize=10.sp)
  Spacer(Modifier.height(8.dp))
 }
}
@Composable private fun CardRow(p:Pick,ink:Color,muted:Color,surface:Color,saved:Set<String>,play:(Pick)->Unit,save:(String)->Unit){
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(surface).border(1.dp,ink.copy(alpha=.22f)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
  Cover(p,Modifier.size(78.dp).clickable{play(p)})
  Column(Modifier.weight(1f).padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(if(p.music)"Music"else"Video",color=mint,fontSize=9.sp);Text(p.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=2);Text(p.creator,color=muted,fontSize=11.sp);Text(p.reason,color=muted,fontSize=10.sp)}
  Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(p.id in saved)"♥"else"♡",color=magenta,fontSize=19.sp,modifier=Modifier.clickable{save(p.id)}.padding(5.dp));Text("▶",color=magenta,modifier=Modifier.clickable{play(p)}.padding(5.dp))}
 }
}
@Composable private fun Cover(p:Pick,m:Modifier=Modifier){Box(m.clip(RoundedCornerShape(0.dp)).background(Brush.linearGradient(p.colors)),contentAlignment=Alignment.Center){Text(p.title.take(1),color=Color.White.copy(alpha=.2f),fontSize=72.sp,fontWeight=FontWeight.Black);Text(if(p.music)"♫"else"▷",color=Color.White,fontSize=30.sp)}}
@Composable private fun Disc(p:Pick,m:Modifier=Modifier){Box(m.clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF5D526B),Color(0xFF17131F),Color(0xFF050408)))),contentAlignment=Alignment.Center){Box(Modifier.fillMaxSize(.48f).clip(CircleShape).background(Brush.linearGradient(p.colors)),contentAlignment=Alignment.Center){Box(Modifier.size(9.dp).clip(CircleShape).background(bgDark))}}}
@Composable private fun Shelf(title:String,detail:String,ink:Color){Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title.uppercase(),color=ink,fontSize=17.sp,fontWeight=FontWeight.Black,letterSpacing=(-.3).sp);Text(detail.uppercase(),color=ink.copy(alpha=.55f),fontSize=9.sp,letterSpacing=.7.sp)};Box(Modifier.fillMaxWidth().height(2.dp).background(ink))}}
