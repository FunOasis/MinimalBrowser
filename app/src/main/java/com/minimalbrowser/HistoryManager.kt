package com.minimalbrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val timestamp: Long
)

class HistoryManager(context: Context) {

    private val db: SQLiteDatabase

    companion object {
        private const val TABLE    = "history"
        private const val MAX_ROWS = 1000
    }

    init {
        db = object : SQLiteOpenHelper(context, "history.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE $TABLE (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        url       TEXT NOT NULL,
                        title     TEXT,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
            override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE")
                onCreate(db)
            }
        }.writableDatabase
    }

    suspend fun save(url: String, title: String) = withContext(Dispatchers.IO) {
        // Skip duplicates
        val cursor = db.rawQuery(
            "SELECT url FROM $TABLE ORDER BY timestamp DESC LIMIT 1", null)
        val lastUrl = if (cursor.moveToFirst()) cursor.getString(0) else ""
        cursor.close()
        if (lastUrl == url) return@withContext

        val cv = ContentValues().apply {
            put("url",       url)
            put("title",     title)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert(TABLE, null, cv)

        // Keep only last MAX_ROWS entries
        db.execSQL("""
            DELETE FROM $TABLE WHERE id NOT IN
            (SELECT id FROM $TABLE ORDER BY timestamp DESC LIMIT $MAX_ROWS)
        """)
    }

    suspend fun getAll(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<HistoryEntry>()
        val cursor = db.query(TABLE, null, null, null, null, null, "timestamp DESC")
        while (cursor.moveToNext()) {
            list.add(HistoryEntry(
                id        = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                url       = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                title     = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: "",
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
            ))
        }
        cursor.close()
        list
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.delete(TABLE, null, null)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.delete(TABLE, "id=?", arrayOf(id.toString()))
    }
}
