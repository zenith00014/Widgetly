package com.kato.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class KatoNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = when (sbn.packageName) {
            "com.whatsapp" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.instagram.android" -> "Instagram"
            "com.facebook.orca" -> "Messenger"
            else -> return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()?.take(100) ?: source
        val message = extras.getCharSequence("android.text")?.toString()?.take(500) ?: return
        if (message.isBlank()) return

        val prefs = getSharedPreferences("kato_preferences", MODE_PRIVATE)
        val previous = try {
            JSONArray(prefs.getString("kato_notifications", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        val next = JSONArray()
        next.put(
            JSONObject()
                .put("source", source)
                .put("title", title)
                .put("message", message)
                .put("time", System.currentTimeMillis())
        )
        for (index in 0 until minOf(previous.length(), 49)) {
            next.put(previous.getJSONObject(index))
        }
        prefs.edit().putString("kato_notifications", next.toString()).apply()
    }
}
