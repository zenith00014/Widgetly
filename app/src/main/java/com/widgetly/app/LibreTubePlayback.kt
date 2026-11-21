/*
 * Playback integration adapted from LibreTube.
 * Copyright (C) LibreTube contributors.
 * Licensed under the GNU General Public License, version 3 or (at your option) any later version.
 * See THIRD_PARTY_NOTICES.md and LICENSES/LibreTube-GPL-3.0-or-later.txt.
 */
package com.widgetly.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/**
 * Uses the same NewPipe extraction path used by LibreTube to resolve YouTube
 * metadata and selectable audio/video stream URLs.
 */
data class ResolvedMedia(val url: String, val extension: String, val mimeType: String, val qualityLabel: String, val subtitles: List<ResolvedSubtitle> = emptyList())
data class ResolvedSubtitle(val url: String, val languageTag: String, val label: String, val mimeType: String)

object LibreTubePlayback {
    @Volatile private var initialized = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(object : Downloader() {
                    @Throws(IOException::class, ReCaptchaException::class)
                    override fun execute(request: Request): Response {
                        val body = request.dataToSend()?.toRequestBody()
                        val builder = okhttp3.Request.Builder()
                            .url(request.url())
                            .method(request.httpMethod(), body)
                            .header("User-Agent", USER_AGENT)
                        request.headers().forEach { (key, values) ->
                            builder.removeHeader(key)
                            values.forEach { value -> builder.addHeader(key, value) }
                        }
                        client.newCall(builder.build()).execute().use { response ->
                            if (response.code == 429) {
                                throw ReCaptchaException("YouTube requested a verification challenge", request.url())
                            }
                            val responseBody = response.body?.string().orEmpty()
                            return Response(
                                response.code,
                                response.message,
                                response.headers.toMultimap(),
                                responseBody,
                                response.request.url.toString()
                            )
                        }
                    }
                })
                org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setPoTokenProvider(
                    com.widgetly.app.potoken.RushPoTokenProvider.instance
                )
                initialized = true
            }
        }
    }

    fun resolve(input: String, audioOnly: Boolean): String =
        resolveMedia(input, audioOnly).url

    fun resolveMedia(input: String, audioOnly: Boolean, maxHeight: Int? = null, maxAudioBitrate: Int? = null, preferMp4: Boolean = false, preferDash: Boolean = false, allowDashFallback: Boolean = false): ResolvedMedia {
        initialize()
        val videoId = extractVideoId(input)
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        if (audioOnly) {
            val audioCandidates = if (maxAudioBitrate == null) info.audioStreams
                else info.audioStreams.filter { it.averageBitrate / 1000 <= maxAudioBitrate }
            val stream = audioCandidates.filter { it.isUrl && it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }
                ?: info.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
                    .minByOrNull { kotlin.math.abs(it.averageBitrate / 1000 - (maxAudioBitrate ?: 0)) }
            if (stream == null) {
                val manifest = com.github.libretube.player.manifest.SabrManifestFactory.from(videoId, info, audioOnly = true)
                    ?: throw IOException("No playable audio stream was found.")
                com.github.libretube.player.manifest.SabrManifestStore.put(videoId, manifest)
                return ResolvedMedia("rush-sabr://youtube/$videoId", "sabr", "application/x-rush-sabr", "Adaptive audio")
            }
            val format = stream.format
            return ResolvedMedia(
                stream.content,
                format?.suffix?.takeIf { it.isNotBlank() } ?: "m4a",
                format?.mimeType?.takeIf { it.isNotBlank() } ?: if (format?.suffix.equals("webm", ignoreCase = true)) "audio/webm" else "audio/mp4",
                "${stream.averageBitrate / 1000} kbps"
            )
        }
        val hasProgressiveVideo = info.videoStreams.any { it.isUrl && it.content.isNotBlank() }
        if (!preferMp4 && (preferDash || (allowDashFallback && !hasProgressiveVideo))) {
            fun dashFallback(): ResolvedMedia? = info.dashMpdUrl.takeIf { it.isNotBlank() }?.let {
                ResolvedMedia(
                    it,
                    "mpd",
                    "application/dash+xml",
                    "Adaptive DASH",
                    runCatching { mapSubtitles(info) }.getOrDefault(emptyList())
                )
            }
            fun hlsFallback(): ResolvedMedia? = info.hlsUrl.takeIf { it.isNotBlank() }?.let {
                ResolvedMedia(it, "m3u8", "application/x-mpegURL", "Adaptive HLS")
            }

            // On the one-time playback retry, try standard DASH/HLS before repeating a failed SABR request.
            if (preferDash) dashFallback()?.let { return it }
            if (preferDash) hlsFallback()?.let { return it }

            val sabrManifest = com.github.libretube.player.manifest.SabrManifestFactory.from(videoId, info)
            if (sabrManifest != null) {
                com.github.libretube.player.manifest.SabrManifestStore.put(videoId, sabrManifest)
                return ResolvedMedia("rush-sabr://youtube/$videoId", "sabr", "application/x-rush-sabr", "Adaptive SABR")
            }

            if (!preferDash) dashFallback()?.let { return it }
            if (!preferDash) hlsFallback()?.let { return it }
        }
        val formatStreams = if (preferMp4) {
            info.videoStreams.filter { it.format?.suffix.equals("mp4", ignoreCase = true) }
        } else {
            info.videoStreams
        }
        if (preferMp4 && formatStreams.isEmpty()) {
            throw IOException("MP4 is not available for this video. Choose Original format instead.")
        }
        val candidates = if (maxHeight == null) formatStreams else formatStreams.filter { it.height <= maxHeight }
        val stream = candidates.maxByOrNull { it.height }
            ?: formatStreams.minByOrNull { kotlin.math.abs(it.height - (maxHeight ?: 0)) }
            ?: throw IOException("No playable video stream was found.")
        if (!stream.isUrl || stream.content.isBlank()) throw IOException("The extractor did not return a direct stream URL.")
        val format = stream.format
        val subtitles = runCatching { mapSubtitles(info) }.getOrDefault(emptyList())
        return ResolvedMedia(
            stream.content,
            format?.suffix?.takeIf { it.isNotBlank() } ?: "mp4",
            format?.mimeType?.takeIf { it.isNotBlank() } ?: if (format?.suffix.equals("webm", ignoreCase = true)) "video/webm" else "video/mp4",
            "${stream.height}p",
            subtitles
        )
    }

    fun resolveSubtitles(input: String): List<ResolvedSubtitle> {
        initialize()
        val videoId = extractVideoId(input)
        return mapSubtitles(StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId"))
    }

    private fun mapSubtitles(info: StreamInfo): List<ResolvedSubtitle> =
        info.subtitles.filter { it.isUrl && it.content.isNotBlank() }.map {
            ResolvedSubtitle(
                it.content,
                it.languageTag.ifBlank { "und" },
                it.displayLanguageName.ifBlank { it.languageTag },
                it.format?.mimeType?.takeIf { mime -> mime.isNotBlank() } ?: "text/vtt"
            )
        }

    private fun extractVideoId(input: String): String {
        val value = input.trim()
        if (value.matches(Regex("[A-Za-z0-9_-]{11}"))) return value
        val patterns = listOf(
            Regex("(?:youtube\\.com/watch\\?(?:[^#]*&)?v=|youtu\\.be/|youtube\\.com/shorts/|youtube\\.com/embed/)([A-Za-z0-9_-]{11})"),
            Regex("[?&]v=([A-Za-z0-9_-]{11})")
        )
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.get(1) }
            ?: throw IllegalArgumentException("Enter a valid YouTube URL or video ID.")
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
}
