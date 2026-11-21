package com.widgetly.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

data class OfflineMedia(
    val id: String,
    val title: String,
    val creator: String,
    val isAudio: Boolean,
    val extension: String,
    val quality: String,
    val file: File,
    val sourceUrl: String,
    val complete: Boolean,
    val maxHeight: Int? = null,
    val maxAudioBitrate: Int? = null,
    val transcodeToMp3: Boolean = false,
    val preferMp4: Boolean = false,
    val sourceId: String = ""
)

object OfflineDownloads {
    const val WORK_TAG = "rush-offline-download"
    private const val DIRECTORY = "offline-media"

    fun enqueue(
        context: Context,
        id: String,
        title: String,
        creator: String,
        isAudio: Boolean,
        media: ResolvedMedia,
        maxHeight: Int? = null,
        maxAudioBitrate: Int? = null,
        transcodeToMp3: Boolean = false,
        preferMp4: Boolean = false
    ): UUID {
        require(!transcodeToMp3 || isAudio) { "MP3 conversion is only available for audio downloads." }
        val key = listOf(
            id,
            media.extension,
            media.qualityLabel,
            isAudio.toString(),
            maxHeight?.toString().orEmpty(),
            maxAudioBitrate?.toString().orEmpty(),
            preferMp4.toString(),
            transcodeToMp3.toString()
        ).joinToString("|")
        val safeId = safeId(key)
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "$safeId.${media.extension}")
        val metadata = File(directory, "$safeId.json")
        val existingMetadata = runCatching { JSONObject(metadata.readText()) }.getOrNull()
        val existingFile = existingMetadata?.optString("file")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(directory, it) }
        if (
            existingMetadata?.optBoolean("complete", false) == true &&
            existingFile?.isFile == true &&
            existingFile.length() > 0L
        ) {
            return UUID.nameUUIDFromBytes("complete:$safeId".toByteArray())
        }
        val details = JSONObject()
            .put("id", safeId)
            .put("sourceId", id)
            .put("title", title)
            .put("creator", creator)
            .put("isAudio", isAudio)
            .put("extension", media.extension)
            .put("quality", media.qualityLabel)
            .put("file", destination.name)
            .put("sourceUrl", media.url)
            .put("maxHeight", maxHeight ?: 0)
            .put("maxAudioBitrate", maxAudioBitrate ?: 0)
            .put("transcodeToMp3", transcodeToMp3)
            .put("preferMp4", preferMp4)
            .put("complete", destination.exists() && destination.length() > 0)
        metadata.writeText(details.toString())

        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(OfflineDownloadWorker.KEY_ID, safeId)
                    .putString(OfflineDownloadWorker.KEY_URL, media.url)
                    .putString(OfflineDownloadWorker.KEY_EXTENSION, media.extension)
                    .putString(OfflineDownloadWorker.KEY_TITLE, title)
                    .putBoolean(OfflineDownloadWorker.KEY_TRANSCODE_MP3, transcodeToMp3)
                    .build()
            )
            .addTag(WORK_TAG)
            .addTag("$WORK_TAG:$safeId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(safeId),
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    suspend fun resume(context: Context, media: OfflineMedia): UUID? {
        if (media.complete) return null
        val resolved = withContext(Dispatchers.IO) {
            LibreTubePlayback.resolveMedia(
                media.sourceId.ifBlank { media.id },
                media.isAudio,
                media.maxHeight,
                media.maxAudioBitrate,
                media.preferMp4
            )
        }
        return enqueue(
            context,
            media.sourceId.ifBlank { media.id },
            media.title,
            media.creator,
            media.isAudio,
            resolved,
            media.maxHeight,
            media.maxAudioBitrate,
            media.transcodeToMp3,
            media.preferMp4
        )
    }

    fun list(context: Context): List<OfflineMedia> {
        val directory = File(context.filesDir, DIRECTORY)
        return directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { metadata ->
                runCatching {
                    val json = JSONObject(metadata.readText())
                    val file = File(directory, json.getString("file"))
                    OfflineMedia(
                        id = json.getString("id"),
                        title = json.optString("title", "Downloaded media"),
                        creator = json.optString("creator", ""),
                        isAudio = json.optBoolean("isAudio"),
                        extension = json.optString("extension", file.extension),
                        quality = json.optString("quality", ""),
                        file = file,
                        sourceUrl = json.optString("sourceUrl", ""),
                        complete = json.optBoolean("complete") && file.exists() && file.length() > 0,
                        maxHeight = json.optInt("maxHeight", 0).takeIf { it > 0 },
                        maxAudioBitrate = json.optInt("maxAudioBitrate", 0).takeIf { it > 0 },
                        transcodeToMp3 = json.optBoolean("transcodeToMp3", false),
                        preferMp4 = json.optBoolean("preferMp4", false),
                        sourceId = json.optString("sourceId").ifBlank { json.getString("id") }
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.file.lastModified() }
    }

    suspend fun delete(context: Context, media: OfflineMedia) = withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context)
                .cancelUniqueWork(workName(media.id))
                .result
                .get(10, TimeUnit.SECONDS)
        }
        media.file.delete()
        File(media.file.parentFile, "${media.id}.json").delete()
        File(media.file.parentFile, "${media.id}.${media.extension}.part").delete()
        File(media.file.parentFile, "${media.id}.converting.mp3").delete()
    }

    fun storageBytes(context: Context): Long {
        val directory = File(context.filesDir, DIRECTORY)
        return directory.walkTopDown()
            .filter { it.isFile && it.extension != "json" }
            .sumOf { it.length() }
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun workInfos(context: Context): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosByTag(WORK_TAG).get()

    fun workName(id: String) = "rush-download-$id"

    private fun safeId(key: String): String =
        UUID.nameUUIDFromBytes(key.toByteArray()).toString().replace("-", "")
}

class OfflineDownloadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val inputUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val inputExtension = inputData.getString(KEY_EXTENSION)?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: return@withContext Result.failure()
        val transcodeToMp3 = inputData.getBoolean(KEY_TRANSCODE_MP3, false)
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Media file" }

        setForeground(createForegroundInfo(applicationContext, id, title, 0, false))
        val directory = File(applicationContext.filesDir, "offline-media").apply { mkdirs() }
        val metadata = File(directory, "$id.json")
        val metadataDetails = runCatching { JSONObject(metadata.readText()) }.getOrNull()
        val url = metadataDetails?.optString("sourceUrl")?.takeIf { it.isNotBlank() } ?: inputUrl
        val extension = metadataDetails?.optString("extension")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: inputExtension
        val partial = File(directory, "$id.$extension.part")
        val destination = File(directory, "$id.$extension")
        var cancellationHandle: kotlinx.coroutines.DisposableHandle? = null
        if (url.startsWith("rush-sabr://")) {
            if (!downloadSabrAudio(id, title, extension, metadata, partial, destination)) {
                return@withContext Result.retry()
            }
        } else {
        val existingBytes = partial.length()
        fun requestFor(streamUrl: String): Request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", STREAM_USER_AGENT)
            .header("Referer", "https://www.youtube.com/")
            .header("Origin", "https://www.youtube.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept-Encoding", "identity")
            .apply { if (existingBytes > 0) header("Range", "bytes=$existingBytes-") }
            .build()

        var activeCall: okhttp3.Call? = null
        cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) activeCall?.cancel()
        }
        try {
            activeCall = CLIENT.newCall(requestFor(url))
            var response = activeCall!!.execute()
            if (response.code == 403 || response.code == 410) {
                response.close()
                val details = runCatching { JSONObject(metadata.readText()) }.getOrNull()
                    ?: return@withContext Result.failure()
                val sourceId = details.optString("sourceId")
                if (sourceId.isBlank()) return@withContext Result.failure()
                val refreshed = LibreTubePlayback.resolveMedia(
                    sourceId,
                    details.optBoolean("isAudio"),
                    details.optInt("maxHeight", 0).takeIf { it > 0 },
                    details.optInt("maxAudioBitrate", 0).takeIf { it > 0 },
                    details.optBoolean("preferMp4")
                )
                if (refreshed.extension != extension) return@withContext Result.failure()
                details.put("sourceUrl", refreshed.url).put("quality", refreshed.qualityLabel)
                metadata.writeText(details.toString())
                activeCall = CLIENT.newCall(requestFor(refreshed.url))
                response = activeCall!!.execute()
            }
            response.use { response ->
                if (!response.isSuccessful) {
                    return@withContext if (response.code >= 500 || response.code == 429) Result.retry()
                    else Result.failure()
                }
                val contentRange = response.header("Content-Range")
                val rangeStart = contentRange
                    ?.substringAfter("bytes ", "")
                    ?.substringBefore("-")
                    ?.toLongOrNull()
                val rangeTotal = contentRange
                    ?.substringAfterLast("/")
                    ?.toLongOrNull()
                if (response.code == 206 && rangeStart != existingBytes) {
                    if (existingBytes > 0) {
                        partial.delete()
                        return@withContext Result.retry()
                    }
                    return@withContext Result.failure()
                }
                val append = existingBytes > 0 && response.code == 206
                if (existingBytes > 0 && !append) partial.delete()
                val initialBytes = if (append) existingBytes else 0L
                val body = response.body ?: return@withContext Result.retry()
                val expectedBytes = rangeTotal ?: body.contentLength().let { if (it > 0) it + initialBytes else -1L }
                body.byteStream().buffered().use { input ->
                    FileOutputStream(partial, append).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        var downloaded = initialBytes
                        var lastReported = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastReported >= 512 * 1024) {
                                lastReported = downloaded
                                val percent = if (expectedBytes > 0) ((downloaded * 100 / expectedBytes).toInt()).coerceIn(0, 99) else 0
                                setProgress(Data.Builder().putInt("progress", percent).putLong("bytes", downloaded).build())
                                setForeground(createForegroundInfo(applicationContext, id, title, percent, false))
                            }
                        }
                        output.flush()
                    }
                }
                if (expectedBytes > 0L && partial.length() != expectedBytes) {
                    return@withContext Result.retry()
                }
                if (partial.length() == 0L) return@withContext Result.failure()
                if (destination.exists()) destination.delete()
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
            }
        }

            var completedFile = destination
            var completedExtension = extension
            if (transcodeToMp3 && extension != "mp3") {
                setProgress(
                    Data.Builder()
                        .putInt("progress", 100)
                        .putLong("bytes", destination.length())
                        .putString("stage", "Converting to MP3")
                        .build()
                )
                setForeground(createForegroundInfo(applicationContext, id, title, 100, true))
                val converted = File(directory, "$id.converting.mp3")
                converted.delete()
                val command = "-y -i ${quote(destination.absolutePath)} -vn -map_metadata 0 -codec:a libmp3lame -b:a 192k ${quote(converted.absolutePath)}"
                val success = runFfmpeg(command)
                if (!success || !converted.exists() || converted.length() == 0L) {
                    converted.delete()
                    return@withContext Result.failure()
                }
                destination.delete()
                completedFile = File(directory, "$id.mp3")
                if (completedFile.exists()) completedFile.delete()
                if (!converted.renameTo(completedFile)) {
                    converted.copyTo(completedFile, overwrite = true)
                    converted.delete()
                }
                completedExtension = "mp3"
            }

            val json = runCatching { JSONObject(metadata.readText()) }.getOrElse { JSONObject() }
                .put("complete", true)
                .put("file", completedFile.name)
                .put("extension", completedExtension)
            metadata.writeText(json.toString())
            setProgress(Data.Builder().putInt("progress", 100).putLong("bytes", completedFile.length()).build())
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure()
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private suspend fun downloadSabrAudio(
        id: String,
        title: String,
        extension: String,
        metadata: File,
        partial: File,
        destination: File
    ): Boolean {
        val details = runCatching { JSONObject(metadata.readText()) }.getOrElse { JSONObject() }
        val sourceId = details.optString("sourceId").ifBlank { throw IOException("Missing source ID for adaptive audio download.") }
        val maxBitrate = details.optInt("maxAudioBitrate", 0).takeIf { it > 0 }
        val videoId = android.net.Uri.parse(details.optString("sourceUrl")).lastPathSegment
            ?.takeIf { it.isNotBlank() }
            ?: sourceId
        var manifest = com.github.libretube.player.manifest.SabrManifestStore.get(videoId)
        if (manifest == null) {
            val refreshed = LibreTubePlayback.resolveMedia(sourceId, true, maxAudioBitrate = maxBitrate)
            if (!refreshed.url.startsWith("rush-sabr://")) {
                details.put("sourceUrl", refreshed.url)
                    .put("extension", refreshed.extension)
                    .put("file", "$id.${refreshed.extension}")
                    .put("quality", refreshed.qualityLabel)
                metadata.writeText(details.toString())
                partial.delete()
                return false
            }
            manifest = com.github.libretube.player.manifest.SabrManifestStore.get(videoId)
                ?: throw IOException("Could not rebuild the adaptive audio manifest.")
        }

        val audioSet = manifest.adaptationSets.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
            ?: throw IOException("The adaptive stream has no audio track.")
        val candidates = audioSet.representations.filterNotNull()
        val representation = if (maxBitrate == null) {
            candidates.maxByOrNull { it.format.averageBitrate }
        } else {
            candidates.filter { it.format.averageBitrate <= maxBitrate * 1000 }
                .maxByOrNull { it.format.averageBitrate }
                ?: candidates.minByOrNull { kotlin.math.abs(it.format.averageBitrate / 1000 - maxBitrate) }
        } ?: throw IOException("No adaptive audio representation is available.")

        val client = com.github.libretube.player.parser.SabrClient(manifest)
        val formatId = representation.formatId()
        client.selectFormat(representation)
        val init = client.getNextSegment(
            com.github.libretube.player.parser.PlaybackRequest.initRequest(formatId, 0L, 1f)
        ) ?: throw IOException("The adaptive audio stream returned no initialization segment.")
        val endSegment = client.getEndSegmentNumber(formatId)
            ?: throw IOException("The adaptive audio stream did not report its final segment.")

        partial.parentFile?.mkdirs()
        var positionMs = 0L
        var nextSegment = init.sequenceNumber + 1L
        var downloadedBytes = 0L
        partial.delete()
        try {
            FileOutputStream(partial, false).buffered().use { output ->
                init.data.forEach { bytes -> output.write(bytes); downloadedBytes += bytes.size }
                while (nextSegment < endSegment) {
                    coroutineContext.ensureActive()
                    val segment = client.getNextSegment(
                        com.github.libretube.player.parser.PlaybackRequest(
                            formatId,
                            positionMs,
                            1f,
                            nextSegment,
                            positionMs,
                            emptyList()
                        )
                    ) ?: throw IOException("The adaptive audio stream ended before all segments arrived.")
                    segment.data.forEach { bytes -> output.write(bytes); downloadedBytes += bytes.size }
                    positionMs += segment.duration
                    nextSegment = segment.sequenceNumber + 1L
                    val percent = if (manifest.durationMs > 0L)
                        (positionMs * 100L / manifest.durationMs).toInt().coerceIn(0, 99) else 0
                    setProgress(Data.Builder().putInt("progress", percent).putLong("bytes", downloadedBytes).build())
                    setForeground(createForegroundInfo(applicationContext, id, title, percent, false))
                }
                output.flush()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw if (error is IOException) error else IOException("Adaptive audio download failed.", error)
        }

        if (!partial.exists() || partial.length() == 0L) throw IOException("Adaptive audio download was empty.")
        destination.delete()
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        return true
    }

    companion object {
        const val KEY_ID = "download_id"
        const val KEY_URL = "stream_url"
        const val KEY_EXTENSION = "extension"
        const val KEY_TITLE = "title"
        const val KEY_TRANSCODE_MP3 = "transcode_mp3"

        private fun createForegroundInfo(context: Context, id: String, title: String, progress: Int, converting: Boolean): ForegroundInfo {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        DOWNLOAD_CHANNEL,
                        "Offline downloads",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val notificationId = (id.hashCode() and 0x7fffffff).coerceAtLeast(1)
            val openDownloads = Intent(context, MainActivity::class.java)
                .putExtra("open_downloads", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openDownloads,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (converting) "Converting to MP3" else "Downloading media")
                .setContentText(title)
                .setContentIntent(contentIntent)
                .setProgress(100, progress.coerceIn(0, 100), converting)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            return ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }

        private const val DOWNLOAD_CHANNEL = "rush_offline_downloads"
        private const val STREAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
        private suspend fun runFfmpeg(command: String): Boolean =
            suspendCancellableCoroutine { continuation ->
                val session = FFmpegKit.executeAsync(command) { result ->
                    if (continuation.isActive) {
                        continuation.resume(ReturnCode.isSuccess(result.returnCode))
                    }
                }
                continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
                if (!continuation.isActive) FFmpegKit.cancel(session.sessionId)
            }

        private fun quote(path: String): String =
            "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
