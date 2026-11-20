package com.widgetly.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class WidgetlyNotificationListener : NotificationListenerService() {
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
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString("social_notifications", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        next.put(JSONObject().put("source", source).put("title", title).put("message", message).put("time", System.currentTimeMillis()))
        for (i in 0 until minOf(old.length(), 49)) next.put(old.getJSONObject(i))
        prefs.edit().putString("social_notifications", next.toString()).apply()
    }
}
