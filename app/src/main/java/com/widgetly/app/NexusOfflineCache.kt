package com.widgetly.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Text-only offline reader cache. No scripts, trackers, or remote images are copied. */
object NexusOfflineCache {
    private val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build()
    private fun file(context: Context, id: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        val dir = File(context.filesDir,"offline_articles").apply { mkdirs() }
        return File(dir,"$hash.html")
    }

    fun has(context: Context, id: String): Boolean = file(context,id).exists()
    fun read(context: Context, id: String): String? = runCatching { file(context,id).takeIf { it.exists() }?.readText() }.getOrNull()
    fun remove(context: Context, id: String) { runCatching { file(context,id).delete() } }

    suspend fun save(context: Context, item: NexusItem): Boolean = withContext(Dispatchers.IO) {
        if (item.kind != ContentKind.NEWS) return@withContext false
        runCatching {
            val request = Request.Builder().url(item.url).header("User-Agent","Mozilla/5.0 (compatible; NexusReader/1.0)").build()
            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Publisher returned HTTP ${response.code}")
                response.body?.string()?.take(2_000_000) ?: error("Empty article")
            }
            val withoutNoise = html.replace(Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>"), " ")
            val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(withoutNoise)?.groupValues?.get(1)?.let(::plainText)?.takeIf { it.isNotBlank() } ?: item.title
            val paragraphs = Regex("(?is)<p\\b[^>]*>(.*?)</p>").findAll(withoutNoise)
                .map { plainText(it.groupValues[1]) }.filter { it.length > 35 }.take(120).toList()
            if (paragraphs.isEmpty()) error("This publisher did not provide readable article text.")
            val body = paragraphs.joinToString("\n") { "<p>${escapeHtml(it)}</p>" }
            val document = """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta charset="utf-8"><style>body{background:#10110e;color:#ece9df;font:17px/1.7 sans-serif;padding:22px;max-width:760px;margin:auto}h1{font-size:25px;line-height:1.25;color:#c8f23a}p{margin:0 0 1.25em}small{color:#8b8d82}</style></head><body><small>OFFLINE READER · NEXUS</small><h1>${escapeHtml(title)}</h1>$body</body></html>"""
            val target = file(context,item.id)
            target.writeText(document)
            val files = target.parentFile?.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
            files.drop(50).forEach { it.delete() }
            true
        }.getOrDefault(false)
    }

    private fun plainText(value: String): String = value
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace("&nbsp;"," ").replace("&amp;","&").replace("&quot;","\"")
        .replace("&#39;","'").replace("&lt;","<").replace("&gt;",">")
        .replace(Regex("\\s+")," ").trim()

    private fun escapeHtml(value: String): String = value
        .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
}
