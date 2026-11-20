package com.widgetly.app

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class ContentKind { VIDEO, NEWS, MOVIE }

data class NexusItem(
    val id: String,
    val title: String,
    val description: String,
    val image: String?,
    val source: String,
    val url: String,
    val kind: ContentKind,
    val published: String = ""
)

object NexusContentRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    private val feeds = listOf(
        "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml" to "BBC Science",
        "https://www.nasa.gov/rss/dyn/breaking_news.rss" to "NASA",
        "https://www.sciencedaily.com/rss/top/science.xml" to "ScienceDaily"
    )
    private val channels = listOf(
        "UCsXVk37bltHxD1rDPwtNM8Q" to "Kurzgesagt",
        "UCX6b17PVsYBQ0ip5gyeme-Q" to "CrashCourse",
        "UCtFRv9O2AHqOZjjynzrv-xg" to "TED-Ed"
    )

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "NexusAndroid/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Source returned HTTP ${response.code}")
            response.body?.string() ?: error("Empty response")
        }
    }

    suspend fun news(): List<NexusItem> = withContext(Dispatchers.IO) {
        feeds.flatMap { (url, source) ->
            runCatching { parseRss(get(url), source, ContentKind.NEWS) }.getOrDefault(emptyList())
        }.distinctBy { it.id }.take(30)
    }

    suspend fun videos(): List<NexusItem> = withContext(Dispatchers.IO) {
        channels.flatMap { (channel, source) ->
            runCatching {
                parseRss(get("https://www.youtube.com/feeds/videos.xml?channel_id=$channel"), source, ContentKind.VIDEO)
            }.getOrDefault(emptyList())
        }.distinctBy { it.id }.take(24)
    }

    suspend fun movies(): List<NexusItem> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("mediatype:movies AND (collection:feature_films OR collection:classic_tv)", "UTF-8")
        val raw = get("https://archive.org/advancedsearch.php?q=$query&fl[]=identifier,title,description,year&sort[]=downloads+desc&rows=24&page=1&output=json")
        val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until docs.length()).mapNotNull { i ->
            val d = docs.optJSONObject(i) ?: return@mapNotNull null
            val id = d.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = d.optString("title", id).replace(Regex("<[^>]*>"), "").take(140)
            val description = d.optString("description", "Publicly accessible film hosted by the Internet Archive.")
                .replace(Regex("<[^>]*>"), " ").take(320)
            NexusItem(
                id = "ia:$id", title = title, description = description,
                image = "https://archive.org/services/img/$id", source = "Internet Archive",
                url = "https://archive.org/details/$id", kind = ContentKind.MOVIE,
                published = d.optString("year")
            )
        }
    }

    private fun parseRss(xml: String, source: String, kind: ContentKind): List<NexusItem> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val result = mutableListOf<NexusItem>()
        var event = parser.eventType
        var inItem = false
        var tag = ""
        var title = ""
        var link = ""
        var description = ""
        var image: String? = null
        var published = ""
        var videoId = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        inItem = true; title = ""; link = ""; description = ""; image = null; published = ""; videoId = ""
                    } else if (inItem && tag == "link") {
                        val href = parser.getAttributeValue(null, "href")
                        if (!href.isNullOrBlank()) link = href
                    } else if (inItem && (tag == "thumbnail" || tag == "content") && image == null) {
                        image = parser.getAttributeValue(null, "url")
                    } else if (inItem && tag == "videoid") {
                        videoId = ""
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                    val value = parser.text ?: ""
                    when (tag) {
                        "title" -> title += value
                        "link" -> if (link.isBlank()) link = value
                        "description", "summary", "encoded" -> description += value
                        "pubdate", "published", "updated" -> published = value
                        "videoid" -> videoId = value
                    }
                }
                XmlPullParser.END_TAG -> if (inItem && (parser.name.equals("item", true) || parser.name.equals("entry", true))) {
                    if (kind == ContentKind.VIDEO) {
                        if (videoId.isBlank()) videoId = Regex("[?&]v=([^&]+)").find(link)?.groupValues?.get(1).orEmpty()
                        if (videoId.isBlank()) videoId = Regex("/videos/([^?]+)").find(link)?.groupValues?.get(1).orEmpty()
                        if (videoId.isNotBlank()) {
                            link = "https://www.youtube.com/watch?v=$videoId"
                            image = image ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        }
                    }
                    if (title.isNotBlank() && link.isNotBlank()) {
                        val clean = description.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                        result.add(NexusItem(
                            id = link, title = title.trim(), description = clean.take(420),
                            image = image ?: extractImage(description), source = source, url = link,
                            kind = kind, published = published
                        ))
                    }
                    inItem = false
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun extractImage(html: String): String? =
        Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
}
