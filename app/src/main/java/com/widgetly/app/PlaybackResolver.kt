package com.widgetly.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Retries transient extractor failures and forces a fresh stream lookup after
 * the first failure. Callers may invoke this from either the main or IO dispatcher.
 */
suspend fun resolvePlaybackMedia(
    input: String,
    audioOnly: Boolean,
    maxHeight: Int? = null,
    maxAudioBitrate: Int? = null,
    preferMp4: Boolean = false,
    preferDash: Boolean = false,
    allowDashFallback: Boolean = false,
    forceRefresh: Boolean = false
): ResolvedMedia {
    var lastError: Exception? = null
    for (attempt in 0..2) {
        try {
            return withContext(Dispatchers.IO) {
                LibreTubePlayback.resolveMedia(
                    input,
                    audioOnly,
                    maxHeight,
                    maxAudioBitrate,
                    preferMp4,
                    preferDash,
                    allowDashFallback,
                    forceRefresh || attempt > 0
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastError = error
            if (attempt < 2) delay(450L * (attempt + 1))
        }
    }
    throw lastError ?: IOException("Could not resolve a playable stream.")
}
