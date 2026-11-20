/*
 * Routes SABR media items to the LibreTube-derived custom adaptive source.
 */
package com.github.libretube.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.github.libretube.player.manifest.SabrManifestStore
import java.io.IOException

@UnstableApi
class RushMediaSourceFactory(dataSourceFactory: DataSource.Factory) : MediaSource.Factory {
    private val fallback = DefaultMediaSourceFactory(dataSourceFactory)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri ?: throw IllegalArgumentException("A media URI is required.")
        if (uri.scheme == "rush-sabr") {
            val id = uri.lastPathSegment ?: throw IOException("SABR video ID is missing.")
            val manifest = SabrManifestStore.get(id) ?: throw IOException("SABR stream metadata expired. Retry playback.")
            return SabrMediaSource.Factory(manifest).createMediaSource(mediaItem)
        }
        return fallback.createMediaSource(mediaItem)
    }

    override fun getSupportedTypes(): IntArray =
        (fallback.supportedTypes.toList() + C.CONTENT_TYPE_OTHER).distinct().toIntArray()

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        fallback.setDrmSessionManagerProvider(provider); return this
    }
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        fallback.setLoadErrorHandlingPolicy(policy); return this
    }
    override fun setCmcdConfigurationFactory(factory: CmcdConfiguration.Factory): MediaSource.Factory {
        fallback.setCmcdConfigurationFactory(factory); return this
    }
}
