package com.kato.app

import android.text.Html
import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NexusNews(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: Long,
    val imageUrl: String,
    val topic: String,
    val score: Int
)

data class NexusVideo(
    val title: String,
    val url: String,
    val videoId: String,
    val channel: String,
    val publishedAt: Long,
    val thumbnail: String
)

data class NexusMovie(
    val title: String,
    val identifier: String,
    val year: String,
    val licenseUrl: String,
    val thumbnail: String
) {
    val url: String get() = "https://archive.org/details/$identifier"
}

object NexusContentRepository {
    private data class FeedSource(val name: String, val topic: String, val url: String)
    private data class RawEntry(
        val title: String,
        val summary: String,
        val url: String,
        val date: Long,
        val image: String
    )

    private val newsSources = listOf(
        FeedSource("BBC World", "World", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        FeedSource("The Daily Star", "Bangladesh", "https://www.thedailystar.net/frontpage/rss.xml"),
        FeedSource("BBC Technology", "Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
        FeedSource("BBC Science & Environment", "Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"),
        FeedSource("BBC Business", "Business", "https://feeds.bbci.co.uk/news/business/rss.xml"),
        FeedSource("BBC Sport Football", "Football", "https://feeds.bbci.co.uk/sport/football/rss.xml"),
        FeedSource("BBC Education", "Education", "https://feeds.bbci.co.uk/news/education/rss.xml")
    )

    private val keywords = mapOf(
        "World" to listOf("world", "global", "international", "united nations", "war", "diplomacy", "climate"),
        "Bangladesh" to listOf("bangladesh", "dhaka", "bangladeshi", "south asia", "chattogram", "sylhet"),
        "Technology" to listOf("technology", "tech", "artificial intelligence", "ai", "software", "cyber", "robot", "internet"),
        "Science" to listOf("science", "research", "space", "physics", "biology", "discovery", "climate", "astronomy"),
        "Business" to listOf("business", "economy", "market", "trade", "company", "finance", "jobs"),
        "Football" to listOf("football", "soccer", "premier league", "champions league", "fifa", "goal", "club"),
        "Education" to listOf("education", "school", "university", "student", "learning", "research")
    )

    fun fetchNews(selectedTopics: Set<String>, customFeedText: String): List<NexusNews> {
        val topics = selectedTopics.ifEmpty { setOf("World", "Bangladesh", "Technology", "Science") }
        val sources = newsSources.filter { it.topic in topics }.toMutableList()
        customFeedText.lines().map { it.trim() }.filter { it.startsWith("https://") || it.startsWith("http://") }
            .forEachIndexed { index, feed -> sources += FeedSource("Custom source ${index + 1}", "Custom", feed) }

        val all = mutableListOf<NexusNews>()
        sources.distinctBy { it.url }.forEach { source ->
            try {
                readFeed(source.url).forEach { item ->
                    val text = (item.title + " " + item.summary).lowercase(Locale.ROOT)
                    val matches = topics.sumOf { topic -> keywords[topic].orEmpty().count { text.contains(it) } }
                    val topic = if (source.topic != "Custom") source.topic else bestTopic(text, topics)
                    val now = System.currentTimeMillis()
                    val ageHours = if (item.date > 0L) ((now - item.date).coerceAtLeast(0L) / 3_600_000L).toInt() else 72
                    val recency = (48 - ageHours.coerceAtMost(48)).coerceAtLeast(0)
                    all += NexusNews(
                        title = item.title.ifBlank { "Untitled story" },
                        summary = cleanText(item.summary).take(360),
                        url = item.url,
                        source = source.name,
                        publishedAt = item.date,
                        imageUrl = item.image,
                        topic = topic,
                        score = matches * 10 + recency
                    )
                }
            } catch (_: Exception) {
                // A failing publisher feed never prevents other sources from loading.
            }
        }
        return all.filter { it.url.startsWith("http") }
            .distinctBy { it.url }
            .sortedWith(compareByDescending<NexusNews> { it.score }.thenByDescending { it.publishedAt })
            .take(30)
    }

    fun fetchVideos(channelText: String): List<NexusVideo> {
        val channels = channelText.lines().map { it.trim() }.filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            val raw = parts[0].trim()
            val id = Regex("UC[A-Za-z0-9_-]{20,}").find(raw)?.value ?: return@mapNotNull null
            id to parts.getOrNull(1)?.trim().orEmpty().ifBlank { id.take(8) }
        }.distinctBy { it.first }

        val result = mutableListOf<NexusVideo>()
        channels.forEach { (channelId, label) ->
            try {
                readFeed("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId").forEach { item ->
                    val videoId = Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(item.url)?.groupValues?.getOrNull(1)
                        ?: Regex("/embed/([A-Za-z0-9_-]{6,})").find(item.url)?.groupValues?.getOrNull(1)
                        ?: return@forEach
                    result += NexusVideo(
                        title = item.title.ifBlank { "Untitled video" },
                        url = "https://www.youtube.com/watch?v=$videoId",
                        videoId = videoId,
                        channel = label,
                        publishedAt = item.date,
                        thumbnail = item.image.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
                    )
                }
            } catch (_: Exception) {
                // Keep the rest of the followed channels available if one feed is offline.
            }
        }
        return result.distinctBy { it.videoId }.sortedByDescending { it.publishedAt }.take(20)
    }

    fun fetchPublicDomainMovies(): List<NexusMovie> {
        val query = "mediatype:movies AND licenseurl:http*publicdomain*"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val requestUrl = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&fl[]=title&fl[]=date&fl[]=licenseurl&rows=12&page=1&output=json"
        return try {
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 7000
            connection.readTimeout = 9000
            connection.setRequestProperty("User-Agent", "NexusAndroid/0.2 (personal media discovery)")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val docs = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
            (0 until docs.length()).mapNotNull { index ->
                val doc = docs.optJSONObject(index) ?: return@mapNotNull null
                val id = doc.optString("identifier")
                val title = doc.optString("title")
                val license = doc.optString("licenseurl")
                if (id.isBlank() || title.isBlank() || !license.contains("publicdomain", true)) return@mapNotNull null
                NexusMovie(
                    title = title,
                    identifier = id,
                    year = doc.optString("date").take(4),
                    licenseUrl = license,
                    thumbnail = "https://archive.org/services/img/$id"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readFeed(feedUrl: String): List<RawEntry> {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 6500
        connection.readTimeout = 8500
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "NexusAndroid/0.2 (RSS reader)")
        connection.setRequestProperty("Accept", "application/atom+xml, application/rss+xml, application/xml, text/xml")
        return connection.inputStream.use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(input, "UTF-8")
            val items = mutableListOf<RawEntry>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && (parser.name.equals("item", true) || parser.name.equals("entry", true))) {
                    parseEntry(parser)?.let(items::add)
                }
                event = parser.next()
            }
            connection.disconnect()
            items
        }
    }

    private fun parseEntry(parser: XmlPullParser): RawEntry? {
        var depth = 1
        var title = ""
        var summary = ""
        var link = ""
        var date = ""
        var image = ""
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "title" -> { if (title.isBlank()) title = parser.nextText().trim(); depth-- }
                        "description", "summary", "content" -> { if (summary.isBlank()) summary = parser.nextText().trim(); depth-- }
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank()) link = href else if (link.isBlank()) link = parser.nextText().trim().also { depth-- }
                        }
                        "pubdate", "published", "updated", "date" -> { if (date.isBlank()) date = parser.nextText().trim(); depth-- }
                        "thumbnail" -> {
                            val candidate = parser.getAttributeValue(null, "url")
                            if (!candidate.isNullOrBlank() && image.isBlank()) image = candidate
                        }
                        "enclosure" -> {
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val candidate = parser.getAttributeValue(null, "url")
                            if (!candidate.isNullOrBlank() && (type.startsWith("image") || image.isBlank())) image = candidate
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        val plainSummary = cleanText(summary)
        if (image.isBlank()) {
            image = Regex("""<img[^>]+src=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
                .find(summary)?.groupValues?.getOrNull(1).orEmpty()
        }
        return RawEntry(
            title = cleanText(title),
            summary = plainSummary,
            url = link,
            date = parseDate(date),
            image = image
        ).takeIf { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    private fun cleanText(raw: String): String = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("\\s+"), " ").trim()

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )
        formats.forEach { pattern ->
            try { return SimpleDateFormat(pattern, Locale.US).parse(raw)?.time ?: 0L } catch (_: Exception) { }
        }
        return try { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(raw)?.time ?: 0L } catch (_: Exception) { 0L }
    }

    private fun bestTopic(text: String, topics: Set<String>): String =
        topics.maxByOrNull { topic -> keywords[topic].orEmpty().count { text.contains(it) } } ?: "General"
}
