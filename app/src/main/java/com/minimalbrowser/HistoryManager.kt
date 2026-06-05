package com.minimalbrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryEntry(val id: Long, val url: String, val title: String, val timestamp: Long)

class HistoryManager(context: Context) {

    private val db: SQLiteDatabase

    companion object {
        private const val TABLE = "history"
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
                db.execSQL("DROP TABLE IF EXISTS $TABLE"); onCreate(db)
            }
        }.writableDatabase
    }

    fun save(url: String, title: String) {
        // Avoid duplicate consecutive entries
        val cursor = db.rawQuery(
            "SELECT id FROM $TABLE ORDER BY timestamp DESC LIMIT 1", null)
        var lastUrl = ""
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            val c2 = db.query(TABLE, arrayOf("url"), "id=?", arrayOf(id.toString()), null, null, null)
            if (c2.moveToFirst()) lastUrl = c2.getString(0)
            c2.close()
        }
        cursor.close()
        if (lastUrl == url) return

        val cv = ContentValues().apply {
            put("url",       url)
            put("title",     title)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert(TABLE, null, cv)

        // Keep only last 1000 entries for RAM efficiency
        db.execSQL("DELETE FROM $TABLE WHERE id NOT IN (SELECT id FROM $TABLE ORDER BY timestamp DESC LIMIT 1000)")
    }

    fun getAll(): List<HistoryEntry> {
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
        return list
    }

    fun clear() { db.delete(TABLE, null, null) }

    fun delete(id: Long) { db.delete(TABLE, "id=?", arrayOf(id.toString())) }
}
