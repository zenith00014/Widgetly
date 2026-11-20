package com.widgetly.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserPlaylist(val id: String, val name: String, val itemIds: List<String>, val sourceKey: String? = null)

object PlaylistStore {
    private const val KEY = "user_playlists"

    fun load(prefs: SharedPreferences): List<UserPlaylist> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val ids = item.optJSONArray("items") ?: JSONArray()
                add(
                    UserPlaylist(
                        item.optString("id"),
                        item.optString("name"),
                        (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) },
                        item.optString("sourceKey").takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }

    fun create(prefs: SharedPreferences, name: String, firstItemId: String? = null): List<UserPlaylist> {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotBlank()) { "Enter a playlist name." }
        val items = firstItemId?.let(::listOf).orEmpty()
        val updated = load(prefs) + UserPlaylist(UUID.randomUUID().toString(), cleanName, items)
        save(prefs, updated)
        return updated
    }

    fun importCollection(
        prefs: SharedPreferences,
        name: String,
        sourceKey: String,
        itemIds: List<String>
    ): UserPlaylist {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotBlank()) { "Enter a playlist name." }
        require(sourceKey.isNotBlank()) { "A source collection ID is required." }
        val uniqueItems = itemIds.filter(String::isNotBlank).distinct()
        val playlists = load(prefs).toMutableList()
        val existingIndex = playlists.indexOfFirst { it.sourceKey == sourceKey }
        val imported = if (existingIndex >= 0) {
            playlists[existingIndex].copy(name = cleanName, itemIds = uniqueItems)
        } else {
            UserPlaylist(UUID.randomUUID().toString(), cleanName, uniqueItems, sourceKey)
        }
        if (existingIndex >= 0) playlists[existingIndex] = imported else playlists += imported
        save(prefs, playlists)
        return imported
    }

    fun add(prefs: SharedPreferences, playlistId: String, itemId: String): List<UserPlaylist> {
        val updated = load(prefs).map { playlist ->
            if (playlist.id == playlistId && itemId !in playlist.itemIds) {
                playlist.copy(itemIds = playlist.itemIds + itemId)
            } else playlist
        }
        save(prefs, updated)
        return updated
    }

    fun remove(prefs: SharedPreferences, playlistId: String, itemId: String): List<UserPlaylist> {
        val updated = load(prefs).map { playlist ->
            if (playlist.id == playlistId) playlist.copy(itemIds = playlist.itemIds - itemId) else playlist
        }
        save(prefs, updated)
        return updated
    }

    fun delete(prefs: SharedPreferences, playlistId: String): List<UserPlaylist> {
        val updated = load(prefs).filterNot { it.id == playlistId }
        save(prefs, updated)
        return updated
    }

    private fun save(prefs: SharedPreferences, playlists: List<UserPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val ids = JSONArray()
            playlist.itemIds.forEach(ids::put)
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("sourceKey", playlist.sourceKey.orEmpty())
                    .put("items", ids)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}


object QueueStore {
    private const val KEY = "playback_queue"

    fun load(prefs: SharedPreferences): List<String> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    fun add(prefs: SharedPreferences, itemId: String): List<String> {
        if (itemId.isBlank()) return load(prefs)
        val updated = (load(prefs) + itemId).takeLast(500)
        save(prefs, updated)
        return updated
    }

    fun replace(prefs: SharedPreferences, ids: List<String>): List<String> {
        val updated = ids.filter(String::isNotBlank).takeLast(500)
        save(prefs, updated)
        return updated
    }

    fun removeAt(prefs: SharedPreferences, index: Int): List<String> {
        val updated = load(prefs).toMutableList()
        if (index in updated.indices) updated.removeAt(index)
        save(prefs, updated)
        return updated
    }

    fun move(prefs: SharedPreferences, fromIndex: Int, toIndex: Int): List<String> {
        val updated = load(prefs).toMutableList()
        if (fromIndex !in updated.indices || toIndex !in updated.indices) return updated
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        save(prefs, updated)
        return updated
    }

    fun clear(prefs: SharedPreferences): List<String> {
        save(prefs, emptyList())
        return emptyList()
    }

    private fun save(prefs: SharedPreferences, ids: List<String>) {
        val array = JSONArray()
        ids.forEach(array::put)
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}
