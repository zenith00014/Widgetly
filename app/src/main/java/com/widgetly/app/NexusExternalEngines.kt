package com.widgetly.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BEEPER_PACKAGE = "com.beeper.android"
private const val LIBRETUBE_PACKAGE = "com.github.libretube"
private val EngineLime = Color(0xFFC8F23A)
private val EnginePanel = Color(0xFF1A1B17)
private val EngineMuted = Color(0xFF8B8D82)

internal fun openBeeper(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(BEEPER_PACKAGE)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    } else {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$BEEPER_PACKAGE"))
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(market)
    }
}

internal fun openLibreTube(context: Context, url: String? = null) {
    val intent = if (!url.isNullOrBlank()) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(LIBRETUBE_PACKAGE)
    } else {
        context.packageManager.getLaunchIntentForPackage(LIBRETUBE_PACKAGE)
    }
    if (intent != null && (intent.component != null || intent.resolveActivity(context.packageManager) != null)) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$LIBRETUBE_PACKAGE/"))
        store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(store)
        Toast.makeText(context, "Install LibreTube to use Nexus video and music.", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun NexusBeeperLaunchButton() {
    val context = LocalContext.current
    Button(onClick = { openBeeper(context) }, colors = ButtonDefaults.buttonColors(containerColor = EngineLime, contentColor = Color(0xFF0E0F0C))) {
        Text("OPEN BEEPER  ↗", fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun NexusLibreTubePage() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().background(EnginePanel, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LIBRETUBE ENGINE", color = EngineLime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("YouTube + Music", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Use LibreTube for subscriptions, search, playlists, background audio and playback. Nexus hands content to the installed LibreTube app.", color = EngineMuted, fontSize = 12.sp)
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search videos or music") })
            Button(onClick = {
                val q = Uri.encode(query.trim())
                openLibreTube(context, if (q.isBlank()) null else "https://www.youtube.com/results?search_query=$q")
            }, modifier = Modifier.fillMaxWidth(), enabled = query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EngineLime, contentColor = Color(0xFF0E0F0C))) {
                Text("SEARCH IN LIBRETUBE")
            }
            OutlinedButton(onClick = { openLibreTube(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN LIBRETUBE", color = EngineLime)
            }
        }
        Text("LibreTube must be installed separately. Nexus does not pretend an external handoff is embedded playback.", color = EngineMuted, fontSize = 10.sp)
    }
}

@Composable
internal fun NexusLibreTubeVideoHandoff(url: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("OPENING WITH LIBRETUBE", color = EngineLime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("This video is handed to LibreTube so playback, SponsorBlock and player controls remain native to the selected engine.", color = EngineMuted, fontSize = 12.sp)
        Button(onClick = { openLibreTube(context, url) }, colors = ButtonDefaults.buttonColors(containerColor = EngineLime, contentColor = Color(0xFF0E0F0C))) {
            Text("PLAY IN LIBRETUBE")
        }
    }
}
