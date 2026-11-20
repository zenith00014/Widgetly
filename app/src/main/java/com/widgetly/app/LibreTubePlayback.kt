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
data class ResolvedMedia(val url: String, val extension: String, val mimeType: String, val qualityLabel: String)

object LibreTubePlayback {
    @Volatile private var initialized = false
    private val client = OkHttpClient()

    private fun initialize() {
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
                initialized = true
            }
        }
    }

    fun resolve(input: String, audioOnly: Boolean): String =
        resolveMedia(input, audioOnly).url

    fun resolveMedia(input: String, audioOnly: Boolean, maxHeight: Int? = null): ResolvedMedia {
        initialize()
        val videoId = extractVideoId(input)
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val stream = if (audioOnly) {
            info.audioStreams.maxByOrNull { it.averageBitrate }
        } else {
            val candidates = info.videoStreams.filter { maxHeight == null || it.height <= maxHeight }
            (candidates.ifEmpty { info.videoStreams }).maxByOrNull { it.height }
        } ?: throw IOException(if (audioOnly) "No playable audio stream was found." else "No playable video stream was found.")
        if (!stream.isUrl || stream.content.isBlank()) {
            throw IOException("The extractor did not return a direct stream URL.")
        }
        val format = stream.format
        val extension = format?.suffix?.takeIf { it.isNotBlank() } ?: if (audioOnly) "m4a" else "mp4"
        val mimeType = format?.mimeType?.takeIf { it.isNotBlank() } ?: if (audioOnly) "audio/*" else "video/*"
        val quality = if (audioOnly) "${stream.averageBitrate / 1000} kbps" else "${stream.height}p"
        return ResolvedMedia(stream.content, extension, mimeType, quality)
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
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"
}
