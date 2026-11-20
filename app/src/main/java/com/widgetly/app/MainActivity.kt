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

private val Aubergine = Color(0xFF14101F)
private val SurfaceDark = Color(0xFF211A30)
private val SurfaceRaised = Color(0xFF30243F)
private val Paper = Color(0xFFF6F1FB)
private val Muted = Color(0xFFBDB3CA)
private val Magenta = Color(0xFFFF3D81)
private val Amber = Color(0xFFFF9A3D)
private val Mint = Color(0xFF7CF5D0)
private val Lilac = Color(0xFFE9E7F1)

private data class ShelfItem(val id:String,val title:String,val subtitle:String,val type:String,val art:List<Color>,val duration:String)
private val catalog = listOf(
    ShelfItem("neon","Neon Afterglow","Mira Sol · Album","MUSIC",listOf(Color(0xFF8D3C9C),Color(0xFFFF6C83)),"38 min"),
    ShelfItem("orbit","The Quiet Orbit","A short film · 2025","MOVIE",listOf(Color(0xFF243F65),Color(0xFF9A7FC4)),"18 min"),
    ShelfItem("studio","Inside the Sound Lab","Field Notes · Episode 04","VIDEO",listOf(Color(0xFFB65C36),Color(0xFFFFBD64)),"12 min"),
    ShelfItem("bluehour","Blue Hour Tapes","Niko Vale · Playlist","MUSIC",listOf(Color(0xFF235D68),Color(0xFF79B5A8)),"52 min"),
    ShelfItem("paper","A City Made of Paper","Short film · 2024","MOVIE",listOf(Color(0xFF9B5364),Color(0xFFE5A3A1)),"24 min"),
    ShelfItem("slow","Make Space for Slow","Studio Koto · Video","VIDEO",listOf(Color(0xFF4C477F),Color(0xFFB7A4E8)),"9 min")
)
private data class Story(val title:String,val source:String,val time:String,val body:String)
private val stories = listOf(
    Story("The return of the album as a complete world","CULTURE / SOUND","8 MIN AGO","Artists are treating records as carefully sequenced worlds again: artwork, liner notes and transitions are becoming part of the listening experience."),
    Story("Small cinemas are finding a new audience","CULTURE / SCREEN","34 MIN AGO","Independent screening rooms are building communities around focused programmes, filmmaker conversations and repertory nights."),
    Story("Why the internet needs more quiet corners","IDEAS / DIGITAL LIFE","1 HR AGO","A growing group of designers are making finite, intentional spaces online: fewer feeds, clearer endings and more room to pay attention.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RushApp(getSharedPreferences("rush_prefs",Context.MODE_PRIVATE)) }
    }
}

@Composable
private fun RushApp(prefs:android.content.SharedPreferences) {
    var tab by remember { mutableStateOf("Home") }
    var player by remember { mutableStateOf<ShelfItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(prefs.getBoolean("light",false)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var saved by remember { mutableStateOf(prefs.getStringSet("saved",emptySet())?.toSet() ?: emptySet()) }
    var history by remember { mutableStateOf(prefs.getStringSet("history",emptySet())?.toSet() ?: emptySet()) }
    val bg=if(light)Lilac else Aubergine
    val panel=if(light)Color.White else SurfaceDark
    val text=if(light)Color(0xFF241B32) else Paper
    val muted=if(light)Color(0xFF70677D) else Muted
    MaterialTheme(colorScheme=if(light) lightColorScheme(primary=Magenta,background=bg,surface=panel,onSurface=text) else darkColorScheme(primary=Magenta,background=bg,surface=panel,onSurface=text)) {
        Surface(Modifier.fillMaxSize(),color=bg) {
            Column(Modifier.fillMaxSize()) {
                if(player!=null) {
                    PlayerScreen(player!!,playing,{playing=!playing},{player=null},light)
                } else {
                    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=18.dp,top=16.dp,bottom=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column {
                            Text("R U S H",color=if(light)Magenta else Mint,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=4.sp)
                            Text(when(tab){"Discover"->"Find your next thing";"News"->"A little context";"Library"->"Your collection";else->"A calmer kind of feed"},color=text,fontSize=22.sp,fontWeight=FontWeight.Bold)
                        }
                        TextButton(onClick={light=!light;prefs.edit().putBoolean("light",light).apply()}) { Text(if(light)"DARK ◐" else "LIGHT ◑",color=muted,fontSize=10.sp) }
                    }
                    Box(Modifier.weight(1f)) {
                        when(tab) {
                            "Discover" -> DiscoverScreen(query,{query=it},filter,{filter=it},text,muted,panel,saved,{item->player=item;playing=true;history=history+item.id;prefs.edit().putStringSet("history",history).apply()},{id->saved=if(id in saved)saved-id else saved+id;prefs.edit().putStringSet("saved",saved).apply()})
                            "News" -> NewsScreen(text,muted,panel)
                            "Library" -> LibraryScreen(saved,history,text,muted,panel,{id->catalog.find{it.id==id}?.let{player=it;playing=true}},{id->saved=saved-id;prefs.edit().putStringSet("saved",saved).apply()})
                            else -> HomeScreen(text,muted,panel,{item->player=item;playing=true;history=history+item.id;prefs.edit().putStringSet("history",history).apply()},{id->saved=if(id in saved)saved-id else saved+id;prefs.edit().putStringSet("saved",saved).apply()},saved)
                        }
                    }
                    if(catalog.any{it.id in history}) {
                        val current=catalog.first{it.id in history}
                        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp).clip(RoundedCornerShape(16.dp)).background(panel).clickable{player=current;playing=true}.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Artwork(current,Modifier.size(42.dp))
                            Column(Modifier.weight(1f).padding(horizontal=10.dp)) { Text("NOW IN YOUR DECK",fontSize=9.sp,color=muted,letterSpacing=1.sp);Text(current.title,fontSize=13.sp,color=text,fontWeight=FontWeight.SemiBold) }
                            Text("▶",color=Mint,fontSize=17.sp)
                        }
                    }
                    NavigationBar(containerColor=panel,contentColor=text) {
                        listOf("Home","Discover","News","Library").forEach { dest ->
                            NavigationBarItem(selected=tab==dest,onClick={tab=dest},icon={Text(when(dest){"Home"->"⌂";"Discover"->"⌕";"News"->"▤";else->"▣"},fontSize=19.sp)},label={Text(dest,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Magenta,selectedTextColor=Magenta,indicatorColor=if(light)Color(0xFFF8DCE8) else SurfaceRaised,unselectedIconColor=muted,unselectedTextColor=muted))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(text:Color,muted:Color,panel:Color,onPlay:(ShelfItem)->Unit,onSave:(String)->Unit,saved:Set<String>) {
    LazyColumn(contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF692C78),Color(0xFFCC477C),Color(0xFFEF9B4A)))).clickable{onPlay(catalog[0])}.padding(20.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("TODAY'S EDIT  /  01",color=Color.White.copy(alpha=.8f),fontSize=10.sp,letterSpacing=2.sp)
                    Text("Good things,\nwithout the endless scroll.",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.ExtraBold,lineHeight=31.sp)
                    Text("A finite selection for your attention.",color=Color.White.copy(alpha=.9f),fontSize=13.sp)
                    Text("PLAY THE EDIT  →",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                }
            }
        }
        item { SectionTitle("PICKED FOR YOU","06 ITEMS",muted) }
        items(catalog.take(3)) { item->MediaRow(item,text,muted,panel,{onPlay(item)},{onSave(item.id)},item.id in saved) }
        item { SectionTitle("A CHANGE OF PACE","NEXT UP",muted) }
        items(catalog.drop(3)) { item->MediaRow(item,text,muted,panel,{onPlay(item)},{onSave(item.id)},item.id in saved) }
        item { Text("THAT'S THE EDIT. COME BACK WHEN YOU'RE READY.",color=muted,fontSize=9.sp,letterSpacing=1.sp,modifier=Modifier.padding(vertical=12.dp)) }
    }
}

@Composable
private fun DiscoverScreen(query:String,onQuery:(String)->Unit,filter:String,onFilter:(String)->Unit,text:Color,muted:Color,panel:Color,saved:Set<String>,onPlay:(ShelfItem)->Unit,onSave:(String)->Unit) {
    val results=catalog.filter{(filter=="ALL"||it.type==filter)&&(it.title+" "+it.subtitle).contains(query,true)}
    LazyColumn(contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            BasicTextField(value=query,onValueChange=onQuery,singleLine=true,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(panel).padding(16.dp),textStyle=TextStyle(color=text,fontSize=15.sp),decorationBox={inner->if(query.isEmpty())Text("Search music, films, videos…",color=muted);inner()})
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("ALL","MUSIC","MOVIE","VIDEO").forEach{type->FilterChip(selected=filter==type,onClick={onFilter(type)},label={Text(type,fontSize=10.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Magenta,selectedLabelColor=Color.White))}
            }
        }
        item { SectionTitle("THE DISCOVERY SHELF","${results.size} RESULTS",muted) }
        items(results) { item->MediaRow(item,text,muted,panel,{onPlay(item)},{onSave(item.id)},item.id in saved) }
        if(results.isEmpty()) item { Text("Nothing on this shelf. Try another search.",color=muted,modifier=Modifier.padding(20.dp)) }
    }
}

@Composable
private fun NewsScreen(text:Color,muted:Color,panel:Color) {
    var expanded by remember{mutableIntStateOf(-1)}
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("THREE STORIES. NO INFINITE FEED.",color=Mint,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp) }
        items(stories.size) { i->
            val story=stories[i]
            Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable{expanded=if(expanded==i)-1 else i}) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(story.source+"  ·  "+story.time,color=Magenta,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                    Text(story.title,color=text,fontSize=18.sp,fontWeight=FontWeight.Bold,lineHeight=22.sp)
                    if(expanded==i)Text(story.body,color=muted,fontSize=14.sp)
                    Text(if(expanded==i)"CLOSE ARTICLE  ↑" else "READ STORY  ↓",color=Mint,fontSize=10.sp,fontWeight=FontWeight.Bold)
                }
            }
        }
        item { Text("YOU'RE ALL CAUGHT UP.",color=muted,fontSize=10.sp,letterSpacing=2.sp,modifier=Modifier.padding(vertical=12.dp)) }
    }
}

@Composable
private fun LibraryScreen(saved:Set<String>,history:Set<String>,text:Color,muted:Color,panel:Color,onPlay:(String)->Unit,onRemove:(String)->Unit) {
    val itemsSaved=catalog.filter{it.id in saved}
    val recent=catalog.filter{it.id in history}
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("SAVED FOR LATER","${itemsSaved.size} ITEMS",muted) }
        if(itemsSaved.isEmpty())item{EmptyCard("Your saved shelf is empty. Tap the bookmark on anything worth keeping.",panel,muted)}
        items(itemsSaved){item->MediaRow(item,text,muted,panel,{onPlay(item.id)},{onRemove(item.id)},true)}
        item { SectionTitle("RECENTLY PLAYED","${recent.size} ITEMS",muted) }
        if(recent.isEmpty())item{EmptyCard("Things you play will show up here.",panel,muted)}
        items(recent){item->MediaRow(item,text,muted,panel,{onPlay(item.id)},{},false)}
    }
}

@Composable
private fun PlayerScreen(item:ShelfItem,playing:Boolean,onToggle:()->Unit,onBack:()->Unit,light:Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(item.id) { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
    var source by remember(item.id) { mutableStateOf("") }
    var audioOnly by remember(item.id) { mutableStateOf(item.type == "MUSIC") }
    var status by remember(item.id) { mutableStateOf("Paste a YouTube link to play real media.") }
    val text=if(light)Color(0xFF241B32) else Paper
    val muted=if(light)Color(0xFF70677D) else Muted
    val panel=if(light)Color.White else SurfaceDark

    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(playing) { player.playWhenReady = playing }

    Column(Modifier.fillMaxSize().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("←  BACK",color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{onBack()})
            Text(if(audioOnly) "AUDIO PLAYER" else "VIDEO PLAYER",color=Mint,fontSize=10.sp,letterSpacing=2.sp)
            Text("•••",color=muted)
        }
        Artwork(item,Modifier.fillMaxWidth().weight(1f).aspectRatio(1f))
        Text(item.title,color=text,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=2)
        Text(item.subtitle,color=muted,fontSize=12.sp)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!audioOnly,onClick={audioOnly=false},label={Text("VIDEO")})
            FilterChip(selected=audioOnly,onClick={audioOnly=true},label={Text("AUDIO")})
        }
        BasicTextField(
            value=source,
            onValueChange={source=it},
            singleLine=true,
            modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(panel).padding(14.dp),
            textStyle=TextStyle(color=text,fontSize=13.sp),
            decorationBox={inner->if(source.isEmpty())Text("YouTube URL or 11-character video ID",color=muted);inner()}
        )
        Button(
            onClick={
                scope.launch {
                    status="Resolving YouTube stream…"
                    try {
                        val streamUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            LibreTubePlayback.resolve(source,audioOnly)
                        }
                        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(streamUrl))
                        player.prepare()
                        player.play()
                        status=if(audioOnly)"AUDIO STREAM READY" else "VIDEO STREAM READY"
                    } catch (e:Exception) {
                        status=e.localizedMessage ?: "Unable to load this YouTube stream."
                    }
                }
            },
            modifier=Modifier.fillMaxWidth(),
            colors=ButtonDefaults.buttonColors(containerColor=Magenta)
        ) { Text("LOAD AND PLAY",color=Color.White,fontWeight=FontWeight.Bold) }
        androidx.compose.ui.viewinterop.AndroidView(
            factory={ctx->androidx.media3.ui.PlayerView(ctx).apply{this.player=player;useController=true;showBuffering=androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING}},
            update={it.player=player},
            modifier=Modifier.fillMaxWidth().height(if(audioOnly)76.dp else 210.dp)
        )
        Text(status,color=muted,fontSize=10.sp)
        Text("Streams are resolved on demand. YouTube availability and extractor compatibility can change.",color=muted,fontSize=9.sp)
    }
}

@Composable
private fun MediaRow(item:ShelfItem,text:Color,muted:Color,panel:Color,onPlay:()->Unit,onSave:()->Unit,isSaved:Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Artwork(item,Modifier.size(82.dp).clickable{onPlay()})
        Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(item.type,color=Mint,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)
            Text(item.title,color=text,fontSize=15.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(item.subtitle,color=muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(item.duration,color=muted,fontSize=9.sp)
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(isSaved)"♥" else "＋",color=if(isSaved)Magenta else muted,fontSize=19.sp,modifier=Modifier.clickable{onSave()}.padding(6.dp))
            Text("▶",color=Magenta,fontSize=15.sp,modifier=Modifier.clickable{onPlay()}.padding(6.dp))
        }
    }
}

@Composable
private fun Artwork(item:ShelfItem,modifier:Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(item.art)),contentAlignment=Alignment.Center) {
        Text(when(item.type){"MUSIC"->"◉";"MOVIE"->"▰";else->"▻"},color=Color.White.copy(alpha=.88f),fontSize=42.sp,fontWeight=FontWeight.Light)
        Text(item.title.take(1).uppercase(),color=Color.White.copy(alpha=.25f),fontSize=78.sp,fontWeight=FontWeight.Black)
    }
}

@Composable
private fun SectionTitle(title:String,count:String,muted:Color) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
        Text(count,color=muted,fontSize=9.sp)
    }
}

@Composable
private fun EmptyCard(message:String,panel:Color,muted:Color) {
    Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(16.dp)){Text(message,color=muted,fontSize=13.sp,modifier=Modifier.padding(16.dp))}
}
