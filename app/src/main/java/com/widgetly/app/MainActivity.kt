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
private val picks=listOf(
 Pick("v1","The craft behind a great scene","Frame by Frame",false,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"A thoughtful look at visual storytelling."),
 Pick("v2","A field recording from the coast","Open Ear",false,listOf(Color(0xFF27645F),Color(0xFF83B6A2)),"For a quieter moment."),
 Pick("v3","How animation finds its rhythm","Motion Notes",false,listOf(Color(0xFF9C4F43),Color(0xFFE5A45B)),"Selected for its craft and detail."),
 Pick("m1","Late Night Frequencies","Mira Sol",true,listOf(Color(0xFF8D3C9C),Color(0xFFFF6C83)),"A warm late-night electronic set."),
 Pick("m2","Blue Hour Tapes","Niko Vale",true,listOf(Color(0xFF235D68),Color(0xFF79B5A8)),"A mellow collection for slow evenings."),
 Pick("m3","Soft Focus","The Sunday Club",true,listOf(Color(0xFF4C477F),Color(0xFFB7A4E8)),"Gentle sounds with room to breathe.")
)
private data class Story(val title:String,val publisher:String,val source:String,val read:String,val summary:String,val why:String)
private val stories=listOf(
 Story("Independent creators are building smaller, closer communities","The Culture Desk","Independent","4 min","A shift toward direct relationships is changing how creators publish and collaborate.","Selected for its focus on creative culture."),
 Story("The new language of music discovery","Signal Review","Public broadcaster","3 min","Curators and niche radio offer alternatives to endless algorithmic playlists.","Selected for its perspective on music discovery."),
 Story("Why short-form video is learning to slow down","Wireframe","Wire","5 min","Some creators make room for context, craft and complete ideas.","Selected for its perspective on digital media.")
)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{RushApp(getSharedPreferences("rush_prefs",Context.MODE_PRIVATE))}}}
@Composable private fun RushApp(prefs:android.content.SharedPreferences){
 var tab by remember{mutableStateOf("Home")};var light by remember{mutableStateOf(prefs.getBoolean("light",false))}
 var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("All")};var active by remember{mutableStateOf<Pick?>(null)}
 var saved by remember{mutableStateOf(prefs.getStringSet("saved",emptySet())?.toSet()?:emptySet())}
 var history by remember{mutableStateOf(prefs.getStringSet("history",emptySet())?.toList()?:emptyList())}
 val bg=if(light)lilac else bgDark;val surface=if(light)Color.White else panelDark
 val ink=if(light)Color(0xFF241B32) else paper;val muted=if(light)Color(0xFF70677D) else mutedDark
 MaterialTheme(colorScheme=if(light)lightColorScheme(primary=magenta,background=bg,surface=surface,onSurface=ink)else darkColorScheme(primary=magenta,background=bg,surface=surface,onSurface=ink)){
  Surface(Modifier.fillMaxSize(),color=bg){Column(Modifier.fillMaxSize()){
   if(active!=null)RushPlayer(active!!,light){active=null}
   else{
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Column{Text("RUSH",color=if(light)magenta else mint,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp);Text(when(tab){"Discover"->"Find your next favourite";"Downloads"->"Offline library";"Library"->"Your collection";else->"Your daily signal"},color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold)}
     TextButton(onClick={light=!light;prefs.edit().putBoolean("light",light).apply()}){Text(if(light)"Dark theme" else "Light theme",color=muted)}
    }
    Box(Modifier.weight(1f)){
     when(tab){
      "Discover"->LiveDiscover(ink,muted,surface,{result->val pick=Pick(result.videoId,result.title,result.creator,result.isMusic,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)), "Live YouTube result");active=pick;history=listOf(pick.id)+history.filterNot{x->x==pick.id};prefs.edit().putStringSet("history",history.toSet()).apply()})
      "Downloads"->DownloadsScreen(ink,muted,surface,{media->active=Pick(media.id,media.title,media.creator,media.isAudio,listOf(Color(0xFF355C7D),Color(0xFF6C5B7B)),"Downloaded for offline playback",media.file.absolutePath)})
      "Library"->Library(saved,history,ink,muted,surface,{id->picks.find{it.id==id}?.let{active=it}},{id->saved=saved-id;prefs.edit().putStringSet("saved",saved).apply()})
      else->Home(ink,muted,surface,saved,{open(it){active=it;history=listOf(it.id)+history.filterNot{x->x==it.id};prefs.edit().putStringSet("history",history.toSet()).apply()}},{id->saved=toggle(saved,id);prefs.edit().putStringSet("saved",saved).apply()})
     }
    }
    if(history.isNotEmpty())picks.find{it.id==history.first()}?.let{recent->Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp).clip(RoundedCornerShape(15.dp)).background(surface).clickable{active=recent}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Cover(recent,Modifier.size(40.dp));Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text("PICK UP WHERE YOU LEFT OFF",fontSize=9.sp,color=muted);Text(recent.title,fontSize=13.sp,color=ink)};Text("▶",color=magenta)}}
    NavigationBar(containerColor=surface){listOf("Home","Discover","Downloads","Library").forEach{dest->NavigationBarItem(selected=tab==dest,onClick={tab=dest;query=""},icon={Text(when(dest){"Home"->"⌂";"Discover"->"⌕";"Downloads"->"⇩";else->"▣"},fontSize=19.sp)},label={Text(dest,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=magenta,selectedTextColor=magenta,indicatorColor=if(light)Color(0xFFF8DCE8)else raised,unselectedIconColor=muted,unselectedTextColor=muted))}}
   }
  }}
 }
}
private fun toggle(s:Set<String>,id:String)=if(id in s)s-id else s+id
private fun open(p:Pick,cb:(Pick)->Unit){cb(p)}

@Composable private fun Home(ink:Color,muted:Color,surface:Color,saved:Set<String>,play:(Pick)->Unit,save:(String)->Unit){
 LazyColumn(contentPadding=PaddingValues(bottom=18.dp)){
  item{Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)){Text("Tonight's feature",color=ink,fontSize=19.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(surface).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Cover(picks[0],Modifier.width(118.dp).height(158.dp));Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("A Rush pick",color=mint,fontSize=10.sp);Text(picks[0].title,color=ink,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(picks[0].reason,color=muted,fontSize=12.sp);Row{Button(onClick={play(picks[0])},colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text("Play")};TextButton(onClick={save(picks[0].id)}){Text(if(picks[0].id in saved)"Saved" else "Save",color=ink)}}}}}}
  item{Shelf("Now spinning","3 tracks",ink);Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)){picks.filter{it.music}.forEach{p->Column(Modifier.width(108.dp).clickable{play(p)},horizontalAlignment=Alignment.CenterHorizontally){Disc(p,Modifier.size(104.dp));Text(p.title,color=ink,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(p.creator,color=muted,fontSize=10.sp)}}}}
  item{Shelf("Pick up where you left off","Recent listens",ink)}
  items(picks.filter{it.music}){CardRow(it,ink,muted,surface,saved,play,save)}
  item{Shelf("Side A: four picks","A short shelf",ink)}
  items(picks.take(4)){CardRow(it,ink,muted,surface,saved,play,save)}
  item{Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(18.dp)).background(surface).padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("That's side A.",color=ink,fontSize=16.sp,fontWeight=FontWeight.Bold);Text("Nothing more loads unless you ask.",color=muted,fontSize=12.sp);Text("Flip to Discover →",color=magenta,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(10.dp))}}
 }
}
@Composable private fun Discover(query:String,onQuery:(String)->Unit,filter:String,onFilter:(String)->Unit,ink:Color,muted:Color,surface:Color,saved:Set<String>,play:(Pick)->Unit,save:(String)->Unit){
 val found=picks.filter{(filter=="All"||filter=="Music"&&it.music||filter=="Videos"&&!it.music)&&(it.title+" "+it.creator).contains(query,true)}
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("Search",color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold);BasicTextField(query,onQuery,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink,fontSize=15.sp),singleLine=true,decorationBox={inner->if(query.isEmpty())Text("Search music and videos",color=muted);inner()})}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","Music","Videos").forEach{FilterChip(selected=filter==it,onClick={onFilter(it)},label={Text(it)})}}}
  item{Shelf("Selected for you","A finite collection",ink)}
  items(found){CardRow(it,ink,muted,surface,saved,play,save)}
  item{Text(if(found.isEmpty())"No matches in this shelf." else "End of these picks. Search again to explore more.",color=muted,fontSize=11.sp,modifier=Modifier.padding(12.dp))}
 }
}
@Composable private fun LiveDiscover(ink:Color,muted:Color,surface:Color,play:(YouTubeSearchResult)->Unit){
 var query by remember { mutableStateOf("") }
 var filter by remember { mutableStateOf("Videos") }
 var results by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
 var status by remember { mutableStateOf("Search YouTube videos and music.") }
 var loading by remember { mutableStateOf(false) }
 val scope= rememberCoroutineScope()
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("Search YouTube",color=ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(10.dp))
  Row(verticalAlignment=Alignment.CenterVertically) {
   BasicTextField(query,{query=it},Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink,fontSize=15.sp),singleLine=true,decorationBox={inner->if(query.isEmpty())Text("Videos, songs, artists or channels",color=muted);inner()})
   Spacer(Modifier.width(8.dp))
   Button(enabled=query.isNotBlank()&&!loading,onClick={
    scope.launch {
     loading=true;status="Searching…"
     try { results=withContext(Dispatchers.IO){LiveYouTubeSearch.search(query,filter=="Music")};status="${results.size} results" }
     catch(e:Exception){results=emptyList();status=e.localizedMessage?: "Search failed."}
     finally {loading=false}
    }
   }) { Text(if(loading)"…" else "Search") }
  }
  Spacer(Modifier.height(8.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   listOf("Videos","Music").forEach { FilterChip(selected=filter==it,onClick={filter=it;results=emptyList()},label={Text(it)}) }
  }
  Text(status,color=muted,fontSize=11.sp,modifier=Modifier.padding(vertical=8.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   items(results,key={it.videoId}) { result ->
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface).clickable{play(result)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
     Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(raised),contentAlignment=Alignment.Center){Text(if(result.isMusic)"♫" else "▶",color=mint,fontSize=20.sp)}
     Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
      Text(result.title,color=ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
      Text(result.creator + if(result.duration.isNotBlank()) " · ${result.duration}" else "",color=muted,fontSize=11.sp,maxLines=1)
     }
     Text("Play",color=magenta,fontSize=12.sp)
    }
   }
  }
 }
}
@Composable private fun News(ink:Color,muted:Color,surface:Color){
 var expanded by remember{mutableIntStateOf(-1)}
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Text("LIVE BRIEFING  •  CULTURE / SOUND / DIGITAL",color=mint,fontSize=10.sp,fontWeight=FontWeight.Bold)}
  items(stories.size){i->val s=stories[i];Card(colors=CardDefaults.cardColors(containerColor=surface),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable{expanded=if(expanded==i)-1 else i}){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(s.publisher+"  ·  "+s.source+"  ·  "+s.read,color=magenta,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(s.title,color=ink,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(s.summary,color=muted,fontSize=13.sp,maxLines=if(expanded==i)8 else 2);if(expanded==i)Text("Why this: "+s.why,color=mint,fontSize=11.sp)else Text("Read summary ↓",color=mint,fontSize=11.sp)}}}
  item{Text("That's the briefing.",color=ink,fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(12.dp))}
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
 val working=workInfos.associateBy{it.inputData.getString(OfflineDownloadWorker.KEY_ID).orEmpty()}
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

@Composable private fun Library(saved:Set<String>,history:List<String>,ink:Color,muted:Color,surface:Color,play:(String)->Unit,remove:(String)->Unit){
 val kept=picks.filter{it.id in saved};val recent=history.distinct().mapNotNull{id->picks.find{it.id==id}}
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Shelf("Your shelf","Saved items",ink)}
  if(kept.isEmpty())item{Text("Nothing here yet. Use the heart in any player to save it.",color=muted,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface).padding(18.dp))}
  items(kept){pick->CardRow(pick,ink,muted,surface,saved,{play(pick.id)},{remove(pick.id)})}
  item{Shelf("Recently played","History",ink)}
  if(recent.isEmpty())item{Text("Your listening and viewing history will appear here.",color=muted)}
  items(recent){pick->CardRow(pick,ink,muted,surface,saved,{play(pick.id)},{})}
 }
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
 var status by remember(item.id){mutableStateOf(if(item.localPath!=null)"Downloaded file ready." else "Paste a YouTube link or video ID to play.")}
 val ink=if(light)Color(0xFF241B32)else paper;val muted=if(light)Color(0xFF70677D)else mutedDark;val surface=if(light)Color.White else panelDark

 Column(Modifier.fillMaxSize().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("← Back",color=muted,modifier=Modifier.clickable{back()}.padding(8.dp));Text(if(audio)"Music player"else"Video player",color=mint)}
  Spacer(Modifier.weight(.2f))
  if(audio)Disc(item,Modifier.size(240.dp))else Cover(item,Modifier.fillMaxWidth().height(190.dp))
  Text(item.title,color=ink,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(item.creator,color=muted)
  if(item.localPath==null) {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=audio,onClick={audio=true},label={Text("Audio only")});FilterChip(selected=!audio,onClick={audio=false},label={Text("Video")})}
   if(!audio) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0 to "Best",720 to "720p",480 to "480p",360 to "360p").forEach{(height,label)->FilterChip(selected=quality==height,onClick={quality=height},label={Text(label)})}}
   BasicTextField(url,{url=it},modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(surface).padding(15.dp),textStyle=TextStyle(color=ink),singleLine=true,decorationBox={inner->if(url.isEmpty())Text("YouTube URL or video ID",color=muted);inner()})
  }
  Button(onClick={
   val activePlayer=controller
   if(activePlayer==null){status="Connecting to playback service…"}
   else scope.launch{
    status="Resolving stream…"
    try{
     val stream=if(item.localPath!=null)java.io.File(item.localPath).toURI().toString() else withContext(Dispatchers.IO){LibreTubePlayback.resolveMedia(url,audio,quality.takeIf{it>0}).url}
     activePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(stream));activePlayer.prepare();activePlayer.play();status=if(item.localPath!=null)"Playing offline" else "Stream ready"
    }catch(e:Exception){status=e.localizedMessage?:"Unable to load this stream."}
   }
  },enabled=controller!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=magenta)){Text(if(item.localPath!=null)"Play offline" else "Load and play",color=Color.White)}
  if(item.localPath==null) Button(onClick={scope.launch{
   status="Preparing offline download…"
   try{
    val media=withContext(Dispatchers.IO){LibreTubePlayback.resolveMedia(url,audio,quality.takeIf{it>0})}
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
