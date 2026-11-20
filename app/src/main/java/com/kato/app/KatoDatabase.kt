package com.kato.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class SavedItem(
    val id: Long,
    val title: String,
    val url: String,
    val type: String,
    val note: String,
    val addedAt: Long,
    val completed: Boolean,
    val liked: Boolean
)

class KatoDatabase(context: Context) : SQLiteOpenHelper(context, "kato_local.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE saved_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL UNIQUE,
                type TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                added_at INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                liked INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX index_saved_items_added_at ON saved_items(added_at DESC)")
        db.execSQL("CREATE INDEX index_saved_items_liked ON saved_items(liked)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE saved_items ADD COLUMN liked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_items_liked ON saved_items(liked)")
        }
    }

    fun saveItem(title: String, url: String, type: String, note: String = "", liked: Boolean = false): Long {
        val existingId = readableDatabase.rawQuery(
            "SELECT id FROM saved_items WHERE url = ? LIMIT 1", arrayOf(url)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        if (existingId != null) {
            if (liked) setLiked(existingId, true)
            return existingId
        }

        val values = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("type", type)
            put("note", note)
            put("added_at", System.currentTimeMillis())
            put("completed", 0)
            put("liked", if (liked) 1 else 0)
        }
        return writableDatabase.insertWithOnConflict("saved_items", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun listItems(search: String = "", type: String = "All", likedOnly: Boolean = false): List<SavedItem> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (type != "All") {
            clauses += "type = ?"
            args += type
        }
        if (likedOnly) clauses += "liked = 1"
        if (search.isNotBlank()) {
            clauses += "(title LIKE ? OR url LIKE ? OR note LIKE ?)"
            val term = "%" + search.trim() + "%"
            args += term
            args += term
            args += term
        }
        val selection = if (clauses.isEmpty()) null else clauses.joinToString(" AND ")
        val selectionArgs = if (args.isEmpty()) null else args.toTypedArray()
        val result = mutableListOf<SavedItem>()
        readableDatabase.query(
            "saved_items",
            arrayOf("id", "title", "url", "type", "note", "added_at", "completed", "liked"),
            selection, selectionArgs, null, null, "added_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedItem(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    url = cursor.getString(2),
                    type = cursor.getString(3),
                    note = cursor.getString(4),
                    addedAt = cursor.getLong(5),
                    completed = cursor.getInt(6) == 1,
                    liked = cursor.getInt(7) == 1
                )
            }
        }
        return result
    }

    fun setCompleted(id: Long, completed: Boolean) {
        val values = ContentValues().apply { put("completed", if (completed) 1 else 0) }
        writableDatabase.update("saved_items", values, "id = ?", arrayOf(id.toString()))
    }

    fun setLiked(id: Long, liked: Boolean) {
        val values = ContentValues().apply { put("liked", if (liked) 1 else 0) }
        writableDatabase.update("saved_items", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteItem(id: Long) {
        writableDatabase.delete("saved_items", "id = ?", arrayOf(id.toString()))
    }

    fun clearItems() {
        writableDatabase.delete("saved_items", null, null)
    }

    fun exportJson(): String {
        val items = JSONArray()
        listItems().forEach { item ->
            items.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("type", item.type)
                    .put("note", item.note)
                    .put("addedAt", item.addedAt)
                    .put("completed", item.completed)
                    .put("liked", item.liked)
            )
        }
        return JSONObject()
            .put("format", "nexus-library-v2")
            .put("items", items)
            .toString(2)
    }
}
