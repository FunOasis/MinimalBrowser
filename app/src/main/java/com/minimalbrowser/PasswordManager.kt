package com.minimalbrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

data class SavedPassword(
    val id: Long = 0,
    val domain: String,
    val username: String,
    val password: String
)

class PasswordManager(context: Context) {

    private val db: SQLiteDatabase

    companion object {
        private const val DB_NAME    = "passwords.db"
        private const val DB_VERSION = 1
        private const val TABLE      = "passwords"
    }

    init {
        db = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE $TABLE (
                        id       INTEGER PRIMARY KEY AUTOINCREMENT,
                        domain   TEXT NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL
                    )
                """)
            }
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE")
                onCreate(db)
            }
        }.writableDatabase
    }

    fun save(domain: String, username: String, password: String) {
        // Update if exists, insert if not
        val existing = getByDomain(domain).firstOrNull { it.username == username }
        if (existing != null) {
            val cv = ContentValues().apply { put("password", password) }
            db.update(TABLE, cv, "id=?", arrayOf(existing.id.toString()))
        } else {
            val cv = ContentValues().apply {
                put("domain",   domain)
                put("username", username)
                put("password", password)
            }
            db.insert(TABLE, null, cv)
        }
    }

    fun getByDomain(domain: String): List<SavedPassword> {
        val list = mutableListOf<SavedPassword>()
        val cursor = db.query(TABLE, null, "domain LIKE ?",
            arrayOf("%$domain%"), null, null, null)
        while (cursor.moveToNext()) {
            list.add(SavedPassword(
                id       = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                domain   = cursor.getString(cursor.getColumnIndexOrThrow("domain")),
                username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
                password = cursor.getString(cursor.getColumnIndexOrThrow("password"))
            ))
        }
        cursor.close()
        return list
    }

    fun getAll(): List<SavedPassword> {
        val list = mutableListOf<SavedPassword>()
        val cursor = db.query(TABLE, null, null, null, null, null, "domain ASC")
        while (cursor.moveToNext()) {
            list.add(SavedPassword(
                id       = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                domain   = cursor.getString(cursor.getColumnIndexOrThrow("domain")),
                username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
                password = cursor.getString(cursor.getColumnIndexOrThrow("password"))
            ))
        }
        cursor.close()
        return list
    }

    fun delete(id: Long) {
        db.delete(TABLE, "id=?", arrayOf(id.toString()))
    }

    fun exportCsv(outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.write("domain,username,password\n")
            getAll().forEach { p ->
                writer.write("${escapeCsv(p.domain)},${escapeCsv(p.username)},${escapeCsv(p.password)}\n")
            }
        }
    }

    fun importCsv(inputStream: java.io.InputStream): Int {
        var count = 0
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.readLine() // skip header
            var line = reader.readLine()
            while (line != null) {
                val parts = parseCsvLine(line)
                if (parts.size >= 3) {
                    save(parts[0].trim(), parts[1].trim(), parts[2].trim())
                    count++
                }
                line = reader.readLine()
            }
        }
        return count
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    // Generate autofill JS for a given page
    fun autofillScript(domain: String): String {
        val passwords = getByDomain(domain)
        if (passwords.isEmpty()) return ""
        val p = passwords.first()
        val u = p.username.replace("'", "\\'")
        val pw = p.password.replace("'", "\\'")
        return """
            (function() {
                var inputs = document.querySelectorAll('input[type=text], input[type=email], input[name*=user], input[name*=email], input[id*=user], input[id*=email]');
                var passInputs = document.querySelectorAll('input[type=password]');
                if (inputs.length > 0) inputs[0].value = '$u';
                if (passInputs.length > 0) passInputs[0].value = '$pw';
            })();
        """.trimIndent()
    }

    // Detect login form and prompt to save
    fun detectAndSaveScript(): String {
        return """
            (function() {
                document.querySelectorAll('form').forEach(function(form) {
                    form.addEventListener('submit', function() {
                        var user = '';
                        var pass = '';
                        form.querySelectorAll('input').forEach(function(input) {
                            if (input.type === 'password') pass = input.value;
                            if (input.type === 'text' || input.type === 'email') user = input.value;
                        });
                        if (user && pass) {
                            window.PRSPasswordSave && window.PRSPasswordSave(user, pass);
                        }
                    });
                });
            })();
        """.trimIndent()
    }
}
