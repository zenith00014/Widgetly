/*
 * Minimal SABR stream descriptor adapted from LibreTube.
 * Copyright (C) LibreTube contributors. GPL-3.0-or-later.
 */
package com.github.libretube.api.obj
data class PipedStream(
    val url: String, val mimeType: String?, val codec: String?, val bitrate: Int?,
    val width: Int? = null, val height: Int? = null, val fps: Int? = null,
    val audioTrackId: String? = null, val audioTrackLocale: String? = null,
    val audioTrackType: String? = null, val itag: Int, val lastModified: Long,
    val isDrc: Boolean = false, val xtags: String? = null
)
