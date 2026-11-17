package com.widgetly.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

internal const val PREFS_NAME = "widgetly_prefs"
internal const val NOTE_KEY = "quick_note"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB8A7FF),
                background = Color(0xFF101018),
                surface = Color(0xFF1B1B27)
            )) {
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("WIDGETLY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Your home,\nyour widgets.", style = MaterialTheme.typography.headlineLarge)
            Text("Small tools for a more personal home screen.", color = Color.LightGray)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("QUICK NOTE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("2 × 2", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(note.ifBlank { "Your note will appear here." }, style = MaterialTheme.typography.titleLarge)
                    Text("A glanceable note on your Android home screen.", color = Color.LightGray)
                }
            }

            Text("Customize widget", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your note") },
                minLines = 2,
                maxLines = 4
            )
            Button(
                onClick = {
                    prefs.edit().putString(NOTE_KEY, note.trim()).apply()
                    scope.launch {
                        QuickNoteWidget().updateAll(context)
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save and update widget") }
            if (saved) Text("Widget updated.", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                "To add it: long-press your home screen → Widgets → Widgetly → Quick Note.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
