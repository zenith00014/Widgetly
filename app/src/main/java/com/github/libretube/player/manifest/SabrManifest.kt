/*
 * SABR manifest model adapted from LibreTube.
 * Copyright (C) LibreTube contributors. GPL-3.0-or-later.
 */
package com.github.libretube.player.manifest

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.FilterableManifest
import com.github.libretube.api.obj.PipedStream

@UnstableApi
class SabrManifest(
    val videoId: String,
    val serverAbrStreamingUri: Uri,
    val videoPlaybackUstreamerConfig: ByteArray,
    val durationMs: Long,
    audioStreams: List<PipedStream>,
    videoStreams: List<PipedStream>
) : FilterableManifest<SabrManifest?> {
    val adaptationSets: List<AdaptationSet> =
        videoStreams.groupBy { it.mimeType }.map { (_, streams) ->
            AdaptationSet(C.TRACK_TYPE_VIDEO, streams.map(::Representation))
        } + audioStreams.groupBy { it.mimeType + it.audioTrackId }.map { (_, streams) ->
            AdaptationSet(C.TRACK_TYPE_AUDIO, streams.map(::Representation))
        }

    override fun copy(streamKeys: List<StreamKey>): SabrManifest = this
}
