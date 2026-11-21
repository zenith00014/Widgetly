package com.widgetly.app

import com.github.libretube.player.RushMediaSourceFactory

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RushPlaybackService : MediaSessionService() {
    private val STREAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    private var mediaSession: MediaSession? = null
    private var streamCache: SimpleCache? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(STREAM_USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com/",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory)
        val cache = SimpleCache(
            File(cacheDir, "media3-stream-cache"),
            LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L),
            StandaloneDatabaseProvider(this)
        )
        streamCache = cache
        val cachedDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory = RushMediaSourceFactory(cachedDataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25_000,
                90_000,
                1_800,
                4_000
            )
            .setBackBuffer(10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(
            this,
            androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(
                12_000,
                6_000,
                12_000,
                0.65f
            )
        ).apply {
            setParameters(buildUponParameters().setForceHighestSupportedBitrate(false))
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build()
        player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        val sessionActivity = android.app.PendingIntent.getActivity(
            this,
            0,
            android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        serviceScope.launch {
            while (isActive) {
                val activePlayer = mediaSession?.player
                val mediaId = activePlayer?.currentMediaItem?.mediaId.orEmpty()
                if (activePlayer != null && mediaId.isNotBlank()) {
                    PlaybackProgress.save(
                        this@RushPlaybackService,
                        mediaId,
                        activePlayer.currentPosition,
                        activePlayer.duration
                    )
                }
                delay(5_000)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            val mediaId = player.currentMediaItem?.mediaId.orEmpty()
            if (mediaId.isNotBlank()) {
                PlaybackProgress.save(this, mediaId, player.currentPosition, player.duration)
            }
        }
        serviceScope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        streamCache?.release()
        streamCache = null
        super.onDestroy()
    }
}
