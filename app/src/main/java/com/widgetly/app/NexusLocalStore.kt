package com.widgetly.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, private on-device collection. No account or remote sync is involved. */
object NexusLocalStore {
    private const val KEY = "nexus_saved_items"

    fun all(context: Context): List<NexusItem> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val kind = runCatching { ContentKind.valueOf(item.optString("kind")) }.getOrNull() ?: return@mapNotNull null
                NexusItem(
                    id = item.optString("id"), title = item.optString("title"),
                    description = item.optString("description"), image = item.optString("image").takeIf { it.isNotBlank() },
                    source = item.optString("source"), url = item.optString("url"), kind = kind,
                    published = item.optString("published")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun isSaved(context: Context, id: String): Boolean = all(context).any { it.id == id }

    fun save(context: Context, item: NexusItem) {
        val items = all(context).filterNot { it.id == item.id }.toMutableList()
        items.add(0, item)
        persist(context, items.take(200))
    }

    fun remove(context: Context, id: String) {
        persist(context, all(context).filterNot { it.id == id })
    }

    private fun persist(context: Context, items: List<NexusItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id).put("title", item.title)
                .put("description", item.description).put("image", item.image ?: "")
                .put("source", item.source).put("url", item.url)
                .put("kind", item.kind.name).put("published", item.published))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
