package com.widgetly.app

import android.net.Uri
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexusSettingsPanel() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    var dailyItems by remember { mutableIntStateOf(prefs.getInt("daily_item_budget", 20)) }
    var sessionItems by remember { mutableIntStateOf(prefs.getInt("session_limit", 12)) }
    var dailyMinutes by remember { mutableIntStateOf(prefs.getInt("daily_time_budget_minutes", 30)) }
    var saved by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val sources = listOf("BBC Science","NASA","ScienceDaily","Kurzgesagt","CrashCourse","TED-Ed","Internet Archive Movies")
    var sourceRefresh by remember { mutableIntStateOf(0) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            val json = JSONObject()
            prefs.all.forEach { (key,value) -> if (value is String || value is Boolean || value is Int || value is Long || value is Float) json.put(key,value) }
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json.toString(2)) } ?: error("Could not create backup") }
                .onSuccess { backupStatus = "Backup exported." }.onFailure { backupStatus = "Export failed: ${it.localizedMessage}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not read backup")
            val json = JSONObject(raw)
            val editor = prefs.edit().clear()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next(); val value = json.get(key)
                when(value) { is String -> editor.putString(key,value); is Boolean -> editor.putBoolean(key,value); is Int -> editor.putInt(key,value); is Long -> editor.putLong(key,value); is Double -> editor.putFloat(key,value.toFloat()) }
            }
            editor.apply()
        }.onSuccess { backupStatus = "Backup restored. Reopen Nexus to refresh all sections." }.onFailure { backupStatus = "Import failed: ${it.localizedMessage}" }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0F0C)).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("YOUR CONTENT SOURCES",color=Color(0xFFC8F23A),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Choose which curated feeds appear in Discover. These preferences stay on this device.",color=Color(0xFF8B8D82),fontSize=11.sp)
            sources.forEach { source ->
                val key = "source_" + source.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                var enabled by remember(sourceRefresh,source) { mutableStateOf(prefs.getBoolean(key,true)) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(source,color=Color(0xFFECE9DF),fontSize=12.sp)
                    Switch(checked=enabled,onCheckedChange={ enabled=it; prefs.edit().putBoolean(key,it).apply(); sourceRefresh++ })
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DISCOVER LIMITS", color = Color(0xFFC8F23A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Daily item budget · $dailyItems opened items", color = Color(0xFFECE9DF), fontSize = 13.sp)
            Slider(value=dailyItems.toFloat(),onValueChange={dailyItems=it.toInt().coerceIn(5,60)},valueRange=5f..60f,steps=10)
            Text("Daily time budget · $dailyMinutes minutes", color = Color(0xFFECE9DF), fontSize = 13.sp)
            Slider(value=dailyMinutes.toFloat(),onValueChange={dailyMinutes=it.toInt().coerceIn(10,180)},valueRange=10f..180f,steps=16)
            Text("Session size · $sessionItems items", color = Color(0xFFECE9DF), fontSize = 13.sp)
            Slider(value=sessionItems.toFloat(),onValueChange={sessionItems=it.toInt().coerceIn(5,30)},valueRange=5f..30f,steps=4)
            Text("Daily item and active Discover time limits stop new discovery when reached. No streaks or rewards.", color = Color(0xFF8B8D82), fontSize = 11.sp)
            Button(onClick={
                prefs.edit().putInt("daily_item_budget",dailyItems).putInt("daily_time_budget_minutes",dailyMinutes).putInt("session_limit",sessionItems).apply()
                saved=true
            },colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFC8F23A),contentColor=Color(0xFF0E0F0C))) { Text("SAVE LIMITS") }
            if(saved) Text("Preferences saved on this device.",color=Color(0xFFC8F23A),fontSize=10.sp)
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BACKUP & RESTORE",color=Color(0xFFC8F23A),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Export or restore your notes, saved media, selected contact shortcuts, inbox history and preferences as a local JSON file.",color=Color(0xFF8B8D82),fontSize=11.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={exportLauncher.launch("nexus-backup.json")},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFC8F23A),contentColor=Color(0xFF0E0F0C))) { Text("EXPORT") }
                OutlinedButton(onClick={importLauncher.launch(arrayOf("application/json","text/json"))}) { Text("RESTORE") }
            }
            if(backupStatus!=null) Text(backupStatus!!,color=Color(0xFFC8F23A),fontSize=10.sp)
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ANDROID PERMISSIONS",color=Color(0xFFC8F23A),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Inbox previews only appear after you grant Notification Access in Android settings. Nexus cannot read complete private chats.",color=Color(0xFF8B8D82),fontSize=11.sp)
            OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}) { Text("OPEN NOTIFICATION ACCESS") }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("PRIVACY & BEHAVIOUR",color=Color(0xFFC8F23A),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Nexus stores your saved items, notes, contact shortcuts and usage limits on this device. It does not create an account or send analytics.",color=Color(0xFF8B8D82),fontSize=11.sp)
            Text("Autoplay is disabled. Notification previews require Android Notification Access. Contacts are selected individually through Android's picker.",color=Color(0xFF8B8D82),fontSize=11.sp)
            Text("CLEAR PERSONAL DATA",color=Color(0xFFFF9A80),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Permanently removes saved items, notes, selected contacts, local music list, inbox history, usage counters and preferences. Export a backup first if you want to keep them.",color=Color(0xFF8B8D82),fontSize=11.sp)
            OutlinedButton(onClick={showClearConfirmation=true},colors=ButtonDefaults.outlinedButtonColors(contentColor=Color(0xFFFF9A80))) { Text("CLEAR ALL NEXUS DATA") }
        }
        if(showClearConfirmation) AlertDialog(
            onDismissRequest={showClearConfirmation=false},
            title={Text("Clear all personal data?")},
            text={Text("This removes all Nexus data stored on this device. This cannot be undone unless you exported a backup.")},
            confirmButton={TextButton(onClick={
                prefs.edit().clear().apply()
                File(context.filesDir,"offline_articles").deleteRecursively()
                backupStatus="Personal data cleared. Reopen Nexus to start fresh."
                showClearConfirmation=false
            }) { Text("CLEAR DATA",color=Color(0xFFFF9A80)) }},
            dismissButton={TextButton(onClick={showClearConfirmation=false}) { Text("CANCEL") }}
        )
    }
}
