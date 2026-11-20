package com.widgetly.app

import android.content.Context

object PlaybackProgress {
    private const val PREFS = "rush_prefs"

    fun positionMs(context: Context, mediaId: String): Long =
        preferences(context).getLong(positionKey(mediaId), 0L).coerceAtLeast(0L)

    fun save(context: Context, mediaId: String, positionMs: Long, durationMs: Long) {
        if (mediaId.isBlank()) return
        preferences(context).edit()
            .putLong(positionKey(mediaId), positionMs.coerceAtLeast(0L))
            .putLong(durationKey(mediaId), durationMs.coerceAtLeast(0L))
            .apply()
    }

    fun clear(context: Context, mediaId: String) {
        if (mediaId.isBlank()) return
        preferences(context).edit()
            .remove(positionKey(mediaId))
            .remove(durationKey(mediaId))
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun positionKey(mediaId: String) = "position:$mediaId"
    private fun durationKey(mediaId: String) = "duration:$mediaId"
}
