/*
 * Converts NewPipeExtractor metadata into the LibreTube SABR manifest model.
 */
package com.github.libretube.player.manifest

import android.net.Uri
import android.util.Base64
import com.github.libretube.api.obj.PipedStream
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

object SabrManifestFactory {
    fun from(videoId: String, info: StreamInfo, audioOnly: Boolean = false): SabrManifest? {
        val serverUrl = info.serverAbrStreamingUrl.takeIf { it.isNotBlank() } ?: return null
        val config = info.ustreamerConfig.takeIf { it.isNotBlank() } ?: return null
        val audio = info.audioStreams.mapNotNull { it.toPipedStream() }
        val video = if (audioOnly) emptyList() else info.videoOnlyStreams.mapNotNull { it.toPipedStream() }
        if (audio.isEmpty() || (!audioOnly && video.isEmpty())) return null
        val configBytes = runCatching { Base64.decode(config, Base64.URL_SAFE) }.getOrNull() ?: return null
        return SabrManifest(videoId, Uri.parse(serverUrl), configBytes, info.duration.coerceAtLeast(0L) * 1000L, audio, video)
    }

    private fun AudioStream.toPipedStream(): PipedStream? {
        val itag = itagItem?.id ?: return null
        val modified = itagItem?.lastModified ?: return null
        return PipedStream(content, format?.mimeType, codec, bitrate, audioTrackId = audioTrackId,
            audioTrackLocale = audioLocale?.toLanguageTag(), audioTrackType = audioTrackType?.name,
            itag = itag, lastModified = modified, isDrc = itagItem?.isDrc ?: false, xtags = itagItem?.xtags)
    }

    private fun VideoStream.toPipedStream(): PipedStream? {
        val itag = itagItem?.id ?: return null
        val modified = itagItem?.lastModified ?: return null
        return PipedStream(content, format?.mimeType, codec, bitrate, width, height, fps,
            itag = itag, lastModified = modified, isDrc = itagItem?.isDrc ?: false, xtags = itagItem?.xtags)
    }
}
