package com.widgetly.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class FeedSource(
    val kind: String,
    val id: String,
    val name: String,
    val productive: Boolean
)

object FeedPreferences {
    private const val TOPICS_KEY = "widgetly_feed_topics"
    private const val SOURCES_KEY = "widgetly_followed_sources"

    val defaultTopics = listOf(
        "Science", "Technology", "History", "Education", "Design", "Engineering",
        "Economics", "Culture", "Environment", "Sports", "Music", "Health",
        "Language", "Business", "Art", "Documentaries"
    )

    val topicCatalog = listOf(
        "Science", "Technology", "AI and machine learning", "Programming", "Cybersecurity",
        "History", "Geography", "Education", "Languages", "Literature", "Books",
        "Philosophy", "Psychology", "Mathematics", "Space", "Engineering", "Economics",
        "Business", "Startups", "Personal finance", "Investing", "Politics", "Current affairs",
        "World news", "Environment", "Climate", "Health", "Medicine", "Fitness", "Sports",
        "Football", "Basketball", "Cricket", "Esports", "Gaming", "Game development",
        "Design", "Art", "Photography", "Architecture", "Fashion", "Beauty", "Makeup",
        "Cooking", "Food", "Travel", "Cars", "Motorcycles", "Nature", "Wildlife", "Pets",
        "Gardening", "Home improvement", "Music", "Music production", "Classical music",
        "Hip-hop", "K-pop", "Film analysis", "Anime", "Manga", "Podcasts", "Documentaries",
        "Vlogs", "Comedy", "Memes", "Viral clips", "Celebrity gossip", "Pranks",
        "Internet drama", "Rage bait", "Reality TV", "Shorts", "ASMR", "Luxury",
        "Shopping", "Product reviews", "Relationships", "Parenting", "Lifestyle",
        "Meditation", "Self-improvement", "Romance", "Horror", "Internet culture",
        "Streaming", "Live streams", "Board games", "Collecting", "Crafts"
    )

    private val lowAttentionTerms = listOf(
        "brainrot", "meme", "memes", "prank", "pranks", "rage bait", "ragebait",
        "celebrity gossip", "influencer drama", "internet drama", "viral challenge",
        "viral clip", "reaction prank", "clickbait"
    )

    private val contextTerms = listOf(
        "explained", "analysis", "documentary", "documentaries", "research",
        "lecture", "tutorial", "how it works", "deep dive", "history of",
        "investigation", "report", "review", "guide", "science", "engineering"
    )

    fun topics(prefs: SharedPreferences): List<String> {
        val stored = runCatching { JSONArray(prefs.getString(TOPICS_KEY, null) ?: "[]") }
            .getOrDefault(JSONArray())
        val values = (0 until stored.length()).mapNotNull { stored.optString(it).trim().takeIf(String::isNotBlank) }
        return values.distinct().ifEmpty { defaultTopics }
    }

    fun sources(prefs: SharedPreferences): List<FeedSource> {
        val stored = runCatching { JSONArray(prefs.getString(SOURCES_KEY, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until stored.length()) {
                val item = stored.optJSONObject(index) ?: continue
                val kind = item.optString("kind").lowercase(Locale.ROOT)
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (kind !in setOf("channel", "news", "artist") || id.isBlank() || name.isBlank()) continue
                add(FeedSource(kind, id, name, item.optBoolean("productive", false)))
            }
        }
    }

    fun follow(prefs: SharedPreferences, kind: String, id: String, name: String, productive: Boolean = false) {
        val normalizedKind = kind.trim().lowercase(Locale.ROOT)
        val cleanId = id.trim().take(256)
        val cleanName = name.trim().take(160)
        require(normalizedKind in setOf("channel", "news", "artist")) { "Unsupported source type." }
        require(cleanId.isNotBlank() && cleanName.isNotBlank()) { "A source name and ID are required." }
        val updated = sources(prefs).toMutableList()
        val index = updated.indexOfFirst { it.kind == normalizedKind && it.id == cleanId }
        val source = FeedSource(normalizedKind, cleanId, cleanName, productive)
        if (index >= 0) updated[index] = source else updated += source
        writeSources(prefs, updated)
    }

    fun unfollow(prefs: SharedPreferences, kind: String, id: String) {
        val updated = sources(prefs).filterNot {
            it.kind == kind.trim().lowercase(Locale.ROOT) && it.id == id.trim()
        }
        writeSources(prefs, updated)
    }

    fun setProductive(prefs: SharedPreferences, id: String, name: String, enabled: Boolean) {
        val updated = sources(prefs).toMutableList()
        val index = updated.indexOfFirst { it.kind == "channel" && it.id == id.trim() }
        val source = FeedSource("channel", id.trim(), name.trim(), enabled)
        if (index >= 0) updated[index] = updated[index].copy(productive = enabled) else updated += source
        writeSources(prefs, updated)
    }

    fun saveTopics(prefs: SharedPreferences, raw: String): List<String> {
        val array = JSONArray(raw)
        val selected = (0 until array.length())
            .mapNotNull { array.optString(it).trim().takeIf { value -> value in topicCatalog } }
            .distinct()
        require(selected.isNotEmpty()) { "Choose at least one topic." }
        prefs.edit().putString(TOPICS_KEY, JSONArray(selected).toString()).apply()
        return selected
    }

    fun asJson(prefs: SharedPreferences): JSONObject {
        val topicArray = JSONArray(topics(prefs))
        val sourceArray = JSONArray().apply {
            sources(prefs).forEach {
                put(JSONObject().put("kind", it.kind).put("id", it.id).put("name", it.name)
                    .put("productive", it.productive))
            }
        }
        return JSONObject().put("topics", topicArray).put("sources", sourceArray)
    }

    fun rank(title: String, description: String, topics: List<String>): Int {
        val content = (title + " " + description).lowercase(Locale.ROOT)
        val topicScore = topics.count { topic ->
            val term = topic.lowercase(Locale.ROOT)
            term.isNotBlank() && content.contains(term)
        } * 8
        val contextScore = contextTerms.count(content::contains) * 2
        val lowAttentionPenalty = lowAttentionTerms.count(content::contains) * 7
        return topicScore + contextScore - lowAttentionPenalty
    }

    private fun writeSources(prefs: SharedPreferences, sources: List<FeedSource>) {
        val array = JSONArray()
        sources.forEach {
            array.put(JSONObject().put("kind", it.kind).put("id", it.id).put("name", it.name)
                .put("productive", it.productive))
        }
        prefs.edit().putString(SOURCES_KEY, array.toString()).apply()
    }
}
