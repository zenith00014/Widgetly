package com.widgetly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0F0C)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DISCOVER LIMITS", color = Color(0xFFC8F23A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Daily item budget · $dailyItems opened items", color = Color(0xFFECE9DF), fontSize = 13.sp)
            Slider(value=dailyItems.toFloat(),onValueChange={dailyItems=it.toInt().coerceIn(5,60)},valueRange=5f..60f,steps=10)
            Text("Session size · $sessionItems items", color = Color(0xFFECE9DF), fontSize = 13.sp)
            Slider(value=sessionItems.toFloat(),onValueChange={sessionItems=it.toInt().coerceIn(5,30)},valueRange=5f..30f,steps=4)
            Text("The daily limit stops opening new Discover items after you reach it. No streaks or rewards.", color = Color(0xFF8B8D82), fontSize = 11.sp)
            Button(onClick={
                prefs.edit().putInt("daily_item_budget",dailyItems).putInt("session_limit",sessionItems).apply()
                saved=true
            },colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFC8F23A),contentColor=Color(0xFF0E0F0C))) { Text("SAVE LIMITS") }
            if(saved) Text("Preferences saved on this device.",color=Color(0xFFC8F23A),fontSize=10.sp)
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("PRIVACY & BEHAVIOUR",color=Color(0xFFC8F23A),fontSize=11.sp,fontWeight=FontWeight.Bold)
            Text("Nexus stores your saved items, notes, contact shortcuts and usage limits on this device. It does not create an account or send analytics.",color=Color(0xFF8B8D82),fontSize=11.sp)
            Text("Autoplay is disabled. Notification previews require Android Notification Access. Contacts are selected individually through Android's picker.",color=Color(0xFF8B8D82),fontSize=11.sp)
        }
    }
}
