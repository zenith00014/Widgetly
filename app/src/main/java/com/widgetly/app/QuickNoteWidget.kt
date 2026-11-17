package com.widgetly.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class QuickNoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val note = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(NOTE_KEY, "Make today count.") ?: "Make today count."

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF242236)))
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {
                Text("WIDGETLY", style = TextStyle(
                    color = ColorProvider(Color(0xFFB8A7FF)),
                    fontWeight = FontWeight.Bold
                ))
                Spacer(GlanceModifier.height(10.dp))
                Text(note.ifBlank { "Tap to add a note." }, style = TextStyle(
                    color = ColorProvider(Color(0xFFFFFFFF)),
                    fontWeight = FontWeight.Medium
                ))
            }
        }
    }
}

class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}
