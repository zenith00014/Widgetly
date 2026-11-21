package com.widgetly.app

import android.os.Bundle
import androidx.compose.runtime.mutableStateOf

/**
 * Playback settings travel with each MediaItem so service-side recovery still
 * has the original source and user-selected format after the player screen closes.
 */
object PlaybackMetadata {
    const val SOURCE = "com.widgetly.app.playback.SOURCE"
    const val AUDIO_ONLY = "com.widgetly.app.playback.AUDIO_ONLY"
    const val QUALITY = "com.widgetly.app.playback.QUALITY"
    const val PREFER_MP4 = "com.widgetly.app.playback.PREFER_MP4"

    fun extras(
        source: String,
        audioOnly: Boolean,
        quality: Int,
        preferMp4: Boolean
    ): Bundle = Bundle().apply {
        putString(SOURCE, source)
        putBoolean(AUDIO_ONLY, audioOnly)
        putInt(QUALITY, quality)
        putBoolean(PREFER_MP4, preferMp4)
    }
}

data class PlaybackRecoveryMessage(val mediaId: String, val message: String)

object PlaybackRecoveryStatus {
    val current = mutableStateOf<PlaybackRecoveryMessage?>(null)
}
