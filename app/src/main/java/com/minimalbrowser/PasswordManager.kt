package com.minimalbrowser

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class SavedPassword(
    val id: String,
    val domain: String,
    val username: String,
    val password: String
)

class PasswordManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "prs_passwords",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // All passwords stored as JSON array in encrypted prefs
    private fun loadAll(): MutableList<SavedPassword> {
        val json = prefs.getString("passwords", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<SavedPassword>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(SavedPassword(
                id       = obj.getString("id"),
                domain   = obj.getString("domain"),
                username = obj.getString("username"),
                password = obj.getString("password")
            ))
        }
        return list
    }

    private fun saveAll(list: List<SavedPassword>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id",       p.id)
                put("domain",   p.domain)
                put("username", p.username)
                put("password", p.password)
            })
        }
        prefs.edit().putString("passwords", arr.toString()).apply()
    }

    suspend fun save(domain: String, username: String, password: String) =
        withContext(Dispatchers.IO) {
            val list = loadAll()
            val existing = list.indexOfFirst {
                it.domain == domain && it.username == username }
            if (existing >= 0) {
                list[existing] = list[existing].copy(password = password)
            } else {
                list.add(SavedPassword(
                    id       = System.currentTimeMillis().toString(),
                    domain   = domain,
                    username = username,
                    password = password
                ))
            }
            saveAll(list)
        }

    suspend fun getByDomain(domain: String): List<SavedPassword> =
        withContext(Dispatchers.IO) {
            // Exact domain match only — no LIKE %domain% false matches
            val host = domain.removePrefix("www.").lowercase()
            loadAll().filter {
                it.domain.removePrefix("www.").lowercase() == host
            }
        }

    suspend fun getAll(): List<SavedPassword> =
        withContext(Dispatchers.IO) { loadAll() }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        saveAll(loadAll().filter { it.id != id })
    }

    suspend fun exportCsv(outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            outputStream.bufferedWriter().use { w ->
                w.write("domain,username,password\n")
                loadAll().forEach { p ->
                    w.write("${escapeCsv(p.domain)},${escapeCsv(p.username)},${escapeCsv(p.password)}\n")
                }
            }
        }

    suspend fun importCsv(inputStream: InputStream): Int =
        withContext(Dispatchers.IO) {
            var count = 0
            inputStream.bufferedReader().use { r ->
                r.readLine() // skip header
                r.forEachLine { line ->
                    val parts = parseCsvLine(line)
                    if (parts.size >= 3 && parts[0].isNotBlank()) {
                        val list = loadAll()
                        list.add(SavedPassword(
                            id       = System.currentTimeMillis().toString() + count,
                            domain   = parts[0].trim(),
                            username = parts[1].trim(),
                            password = parts[2].trim()
                        ))
                        saveAll(list)
                        count++
                    }
                }
            }
            count
        }

    // Autofill JS — exact domain match only
    suspend fun autofillScript(domain: String): String {
        val passwords = getByDomain(domain)
        if (passwords.isEmpty()) return ""
        val p = passwords.first()
        val u = p.username.replace("'", "\\'")
        val pw = p.password.replace("'", "\\'")
        return """
            (function() {
                var inputs = document.querySelectorAll('input[type=text],input[type=email],input[name*=user],input[name*=email],input[id*=user],input[id*=email]');
                var passInputs = document.querySelectorAll('input[type=password]');
                if (inputs.length > 0) inputs[0].value = '$u';
                if (passInputs.length > 0) passInputs[0].value = '$pw';
            })();
        """.trimIndent()
    }

    private fun escapeCsv(v: String) =
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            "\"${v.replace("\"", "\"\"")}\"" else v

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQ && i+1 < line.length && line[i+1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQ = !inQ
                c == ',' && !inQ -> { result.add(cur.toString()); cur = StringBuilder() }
                else -> cur.append(c)
            }
            i++
        }
        result.add(cur.toString())
        return result
    }
}
