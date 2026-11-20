package com.widgetly.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RetroClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = LocalDateTime.now()
        val time = now.format(DateTimeFormatter.ofPattern("hh:mm", Locale.getDefault()))
        val period = now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
        val date = now.format(DateTimeFormatter.ofPattern("EEE  •  d MMM", Locale.getDefault()))

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF202820)))
                    .padding(16.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("WIDGETLY / TIME", style = TextStyle(
                        color = ColorProvider(Color(0xFFC5E879)),
                        fontWeight = FontWeight.Bold
                    ))
                    Spacer(GlanceModifier.width(6.dp))
                    Text("●", style = TextStyle(color = ColorProvider(Color(0xFFC5E879))))
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(verticalAlignment = Alignment.Vertical.Bottom) {
                    Text(time, style = TextStyle(
                        color = ColorProvider(Color(0xFFF3F1E8)),
                        fontWeight = FontWeight.Bold
                    ))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(period, style = TextStyle(color = ColorProvider(Color(0xFFC5E879))))
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(date, style = TextStyle(color = ColorProvider(Color(0xFFB5BEB0))))
            }
        }
    }
}

class RetroClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RetroClockWidget()
}
