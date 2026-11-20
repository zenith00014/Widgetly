package com.widgetly.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

internal const val PREFS_NAME = "widgetly_prefs"
internal const val NOTE_KEY = "quick_note"

private val Ink = Color(0xFF111411)
private val Panel = Color(0xFF1B211C)
private val PanelRaised = Color(0xFF252D25)
private val Lime = Color(0xFFC5E879)
private val Muted = Color(0xFFA4AEA1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Lime,
                    onPrimary = Ink,
                    background = Ink,
                    surface = Panel,
                    onSurface = Color(0xFFF0F2EA)
                )
            ) {
                WidgetlyHome(this)
            }
        }
    }
}

@Composable
private fun WidgetlyHome(context: Context) {
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var note by remember { mutableStateOf(prefs.getString(NOTE_KEY, "Make today count.") ?: "") }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("WIDGETLY", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Your pocket dashboard.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("●", color = Lime, fontSize = 18.sp)
            }

            Text("CENTERS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            CenterCard("Entertainment", "Your media, one simple place.", "01", listOf(
                CenterAction("YouTube", "com.google.android.youtube", "https://m.youtube.com"),
                CenterAction("Spotify", "com.spotify.music", "https://open.spotify.com")
            ), context)
            CenterCard("Social", "Quick access to your conversations.", "02", listOf(
                CenterAction("WhatsApp", "com.whatsapp", "https://web.whatsapp.com"),
                CenterAction("Telegram", "org.telegram.messenger", "https://web.telegram.org"),
                CenterAction("Instagram", "com.instagram.android", "https://www.instagram.com"),
                CenterAction("Messenger", "com.facebook.orca", "https://www.messenger.com")
            ), context)

            Text("YOUR WIDGETS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("QUICK NOTE", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("2 × 2", color = Muted, fontSize = 11.sp)
                    }
                    Text(note.ifBlank { "Your note will appear here." }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text("A small reminder on your home screen.", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Edit your note") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            prefs.edit().putString(NOTE_KEY, note.trim()).apply()
                            scope.launch {
                                QuickNoteWidget().updateAll(context)
                                saved = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Save widget", fontWeight = FontWeight.Bold) }
                    if (saved) Text("Widget updated.", color = Lime, fontSize = 12.sp)
                }
            }
            Text("Add Quick Note from your home screen's Widgets menu.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

private data class CenterAction(val label: String, val packageName: String, val webUrl: String)

@Composable
private fun CenterCard(title: String, subtitle: String, marker: String, actions: List<CenterAction>, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(marker, color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(subtitle, color = Muted, fontSize = 13.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { action ->
                            Button(
                                onClick = { openAppOrWeb(context, action) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(action.label, fontSize = 12.sp) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun openAppOrWeb(context: Context, action: CenterAction) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName)
    try {
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.webUrl)))
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open " + action.label, Toast.LENGTH_SHORT).show()
    }
}
