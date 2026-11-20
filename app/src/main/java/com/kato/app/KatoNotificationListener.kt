package com.kato.app

import android.app.Person
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

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
        val title = (
            extras.getCharSequence("android.conversationTitle")
                ?: extras.getCharSequence("android.title")
            )?.toString()?.take(100)?.ifBlank { source } ?: source
        val message = (
            extras.getCharSequence("android.bigText")
                ?: extras.getCharSequence("android.text")
            )?.toString()?.take(500) ?: return
        if (message.isBlank()) return

        val people = try {
            @Suppress("DEPRECATION")
            extras.getParcelableArrayList<Person>("android.people.list")
        } catch (_: Exception) {
            null
        }
        val person = people?.firstOrNull()
        val avatar = person?.icon?.let { icon ->
            try { drawableToBase64(icon.loadDrawable(this)) } catch (_: Exception) { "" }
        }.orEmpty()

        val conversationId = "$source|${title.lowercase().trim()}"
        val prefs = getSharedPreferences("kato_preferences", MODE_PRIVATE)
        val previous = try {
            JSONArray(prefs.getString("kato_notifications", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

        val next = JSONArray()
        var replaced = false
        for (index in 0 until previous.length()) {
            val old = previous.optJSONObject(index) ?: continue
            if (old.optString("id") == conversationId) {
                next.put(
                    JSONObject()
                        .put("id", conversationId)
                        .put("source", source)
                        .put("title", title)
                        .put("message", message)
                        .put("time", System.currentTimeMillis())
                        .put("unread", true)
                        .put("avatar", avatar.ifBlank { old.optString("avatar") })
                )
                replaced = true
            } else if (next.length() < 49) {
                next.put(old)
            }
        }
        if (!replaced) {
            val newest = JSONArray()
            newest.put(
                JSONObject()
                    .put("id", conversationId)
                    .put("source", source)
                    .put("title", title)
                    .put("message", message)
                    .put("time", System.currentTimeMillis())
                    .put("unread", true)
                    .put("avatar", avatar)
            )
            for (index in 0 until minOf(next.length(), 49)) newest.put(next.getJSONObject(index))
            prefs.edit().putString("kato_notifications", newest.toString()).apply()
        } else {
            prefs.edit().putString("kato_notifications", next.toString()).apply()
        }
    }

    private fun drawableToBase64(drawable: Drawable?): String {
        if (drawable == null) return ""
        val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
        bitmap.recycle()
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
