/*
 * PoTokenGenerator adapted from LibreTube.
 * Copyright (C) LibreTube contributors.
 * Licensed under GPL-3.0-or-later. See THIRD_PARTY_NOTICES.md.
 */
package com.widgetly.app.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

class PoTokenGenerator(private val appContext: Context) : PoTokenProvider {
    private val supportsWebView by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private val lock = Any()
    private var visitorData: String? = null
    private var webViewGenerator: PoTokenWebView? = null
    @Volatile private var cachedResult: PoTokenResult? = null

    fun getCachedWebClientPoToken(): PoTokenResult? = cachedResult

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!supportsWebView || videoId.isBlank()) return null
        return runCatching {
            val (generator, visitor) = synchronized(lock) {
                if (webViewGenerator == null || webViewGenerator!!.isExpired() || visitorData.isNullOrBlank()) {
                    val clientInfo = InnertubeClientRequestInfo.ofWebClient()
                    clientInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                    visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                        clientInfo,
                        NewPipe.getPreferredLocalization(),
                        NewPipe.getPreferredContentCountry(),
                        YoutubeParsingHelper.getYouTubeHeaders(),
                        YoutubeParsingHelper.YOUTUBEI_V1_URL,
                        null,
                        false
                    )
                    runBlocking {
                        webViewGenerator?.let { old ->
                            Handler(Looper.getMainLooper()).post { old.close() }
                        }
                        webViewGenerator = PoTokenWebView.newPoTokenGenerator(appContext)
                    }
                }
                webViewGenerator!! to visitorData!!
            }
            val token = runBlocking { generator.generatePoToken(videoId) }
            PoTokenResult(visitor, token, token).also { cachedResult = it }
        }.onFailure { Log.w(TAG, "Could not generate YouTube proof-of-origin token; continuing without one", it) }
            .getOrNull()
    }

    override fun getWebEmbedClientPoToken(videoId: String?): PoTokenResult? = null
    override fun getAndroidClientPoToken(videoId: String?): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String?): PoTokenResult? = null

    companion object { private const val TAG = "RushPoToken" }
}
