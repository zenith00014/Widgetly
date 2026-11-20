package com.widgetly.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

data class OfflineMedia(
    val id: String,
    val title: String,
    val creator: String,
    val isAudio: Boolean,
    val extension: String,
    val quality: String,
    val file: File,
    val sourceUrl: String,
    val complete: Boolean
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
        media: ResolvedMedia
    ): UUID {
        val safeId = safeId(id, media.url)
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "$safeId.${media.extension}")
        val metadata = File(directory, "$safeId.json")
        val details = JSONObject()
            .put("id", safeId)
            .put("title", title)
            .put("creator", creator)
            .put("isAudio", isAudio)
            .put("extension", media.extension)
            .put("quality", media.qualityLabel)
            .put("file", destination.name)
            .put("sourceUrl", media.url)
            .put("complete", destination.exists() && destination.length() > 0)
        metadata.writeText(details.toString())

        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(OfflineDownloadWorker.KEY_ID, safeId)
                    .putString(OfflineDownloadWorker.KEY_URL, media.url)
                    .putString(OfflineDownloadWorker.KEY_EXTENSION, media.extension)
                    .build()
            )
            .addTag(WORK_TAG)
            .addTag("$WORK_TAG:$safeId")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(safeId),
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    fun resume(context: Context, media: OfflineMedia): UUID? {
        if (media.complete) return null
        val restored = ResolvedMedia(media.sourceUrl, media.extension, "*/*", media.quality)
        return enqueue(context, media.id, media.title, media.creator, media.isAudio, restored)
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
                        complete = json.optBoolean("complete") && file.exists() && file.length() > 0
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.file.lastModified() }
    }

    fun delete(context: Context, media: OfflineMedia) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(media.id))
        media.file.delete()
        File(media.file.parentFile, "${media.id}.json").delete()
        File(media.file.parentFile, "${media.id}.${media.extension}.part").delete()
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun workInfos(context: Context): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosByTag(WORK_TAG).get()

    fun workName(id: String) = "rush-download-$id"

    private fun safeId(id: String, url: String): String {
        val normalized = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (normalized.length in 8..64) normalized
        else UUID.nameUUIDFromBytes("$id|$url".toByteArray()).toString().replace("-", "")
    }
}

class OfflineDownloadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val extension = inputData.getString(KEY_EXTENSION)?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: return@withContext Result.failure()

        val directory = File(applicationContext.filesDir, "offline-media").apply { mkdirs() }
        val partial = File(directory, "$id.$extension.part")
        val destination = File(directory, "$id.$extension")
        val metadata = File(directory, "$id.json")
        val existingBytes = partial.length()
        val request = Request.Builder()
            .url(url)
            .apply { if (existingBytes > 0) header("Range", "bytes=$existingBytes-") }
            .build()

        try {
            CLIENT.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext if (response.code >= 500 || response.code == 429) Result.retry()
                    else Result.failure()
                }
                val append = existingBytes > 0 && response.code == 206
                if (existingBytes > 0 && !append) partial.delete()
                val initialBytes = if (append) existingBytes else 0L
                val body = response.body ?: return@withContext Result.retry()
                val expectedBytes = body.contentLength().let { if (it > 0) it + initialBytes else -1L }
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
                            }
                        }
                        output.flush()
                    }
                }
                if (partial.length() == 0L) return@withContext Result.failure()
                if (destination.exists()) destination.delete()
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
            }

            val json = runCatching { JSONObject(metadata.readText()) }.getOrElse { JSONObject() }
                .put("complete", true)
                .put("file", destination.name)
            metadata.writeText(json.toString())
            setProgress(Data.Builder().putInt("progress", 100).putLong("bytes", destination.length()).build())
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_ID = "download_id"
        const val KEY_URL = "stream_url"
        const val KEY_EXTENSION = "extension"
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
