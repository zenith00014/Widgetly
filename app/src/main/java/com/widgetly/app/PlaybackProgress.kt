package com.widgetly.app

import android.content.Context

object PlaybackProgress {
    private const val PREFS = "rush_prefs"

    fun positionMs(context: Context, mediaId: String): Long {
        val prefs = preferences(context)
        val position = prefs.getLong(positionKey(mediaId), 0L).coerceAtLeast(0L)
        val duration = prefs.getLong(durationKey(mediaId), 0L).coerceAtLeast(0L)
        return if (duration > 0L && position >= duration - 15_000L) 0L else position
    }

    fun durationMs(context: Context, mediaId: String): Long =
        preferences(context).getLong(durationKey(mediaId), 0L).coerceAtLeast(0L)

    fun resumePositionMs(context: Context, mediaId: String): Long {
        val position = positionMs(context, mediaId)
        val duration = durationMs(context, mediaId)
        return if (duration > 0 && position >= duration - 15_000L) 0L else position
    }

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
