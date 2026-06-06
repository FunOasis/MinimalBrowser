package com.minimalbrowser

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

class AdBlocker private constructor(private val context: Context) {

    // Constants
    companion object {
        private const val TAG              = "AdBlocker"
        private const val HOST_CAPACITY    = 100_000
        private const val PATTERN_CAPACITY = 5_000
        private const val COSMETIC_CAP     = 500
        private const val CACHE_SIZE       = 5_000

        @Volatile private var instance: AdBlocker? = null

        fun get(context: Context): AdBlocker =
            instance ?: synchronized(this) {
                instance ?: AdBlocker(context.applicationContext).also { instance = it }
            }

        fun reset() { instance = null }
    }

    private val blockedHosts    = HashSet<String>(HOST_CAPACITY)
    private val urlPatterns     = ArrayList<String>(PATTERN_CAPACITY)
    private val cosmeticRules   = ArrayList<String>(COSMETIC_CAP)
    private val dynamicFilters  = ArrayList<Regex>()  // precompiled

    // LruCache — avoids re-evaluating same URL repeatedly
    private val decisionCache   = LruCache<String, Boolean>(CACHE_SIZE)

    init {
        // Load on IO thread — don't block startup
        CoroutineScope(Dispatchers.IO).launch {
            loadHosts(context)
            loadPatterns(context)
            loadCosmetic(context)
            loadCustomFilters()
            Log.i(TAG, "Loaded ${blockedHosts.size} hosts, " +
                "${urlPatterns.size} patterns, " +
                "${cosmeticRules.size} cosmetic, " +
                "${dynamicFilters.size} dynamic")
        }
    }

    private fun loadHosts(ctx: Context) {
        readAssetLines(ctx, "blocklist.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!')) {
                val parts = line.trim().split("\\s+".toRegex())
                val host = if (parts.size >= 2) parts[1] else parts[0]
                if (host.isNotBlank() && host != "0.0.0.0" && host != "127.0.0.1")
                    blockedHosts.add(host.lowercase())
            }
        }
    }

    private fun loadPatterns(ctx: Context) {
        readAssetLines(ctx, "filters.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!'))
                urlPatterns.add(line.trim().lowercase())
        }
    }

    private fun loadCosmetic(ctx: Context) {
        readAssetLines(ctx, "cosmetic.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!'))
                cosmeticRules.add(line.trim())
        }
    }

    private fun loadCustomFilters() {
        Prefs.get(context).customFilters.lines().forEach { line ->
            val t = line.trim()
            if (t.isBlank() || t.startsWith('#')) return@forEach
            if (t.contains("/") || t.contains("*")) {
                // Precompile regex once
                try {
                    val pattern = t.replace(".", "\\.").replace("*", ".*")
                    dynamicFilters.add(Regex(pattern))
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid filter pattern: $t")
                }
            } else {
                blockedHosts.add(t.lowercase())
            }
        }
    }

    fun reloadCustomFilters() {
        dynamicFilters.clear()
        decisionCache.evictAll()
        loadCustomFilters()
    }

    fun isBlocked(url: String): Boolean {
        if (url.isBlank()) return false

        // Check cache first
        decisionCache.get(url)?.let { return it }

        val lower = url.lowercase()
        val result = checkBlocked(lower)

        decisionCache.put(url, result)
        return result
    }

    private fun checkBlocked(lower: String): Boolean {
        val host = extractHost(lower)
        if (host != null && isHostBlocked(host)) return true

        for (pattern in urlPatterns) {
            if (lower.contains(pattern)) return true
        }

        for (regex in dynamicFilters) {
            if (regex.containsMatchIn(lower)) return true
        }

        return false
    }

    fun cosmeticCSS(): String {
        if (cosmeticRules.isEmpty()) return ""
        return cosmeticRules.joinToString(",\n") + " { display: none !important; }\n"
    }

    fun cosmeticScript(): String {
        val css = cosmeticCSS()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        if (css.isBlank()) return ""
        return """
            (function() {
                var s = document.createElement('style');
                s.type = 'text/css';
                s.innerHTML = "$css";
                if (document.head) document.head.appendChild(s);
            })();
        """.trimIndent()
    }

    private fun isHostBlocked(host: String): Boolean {
        if (blockedHosts.contains(host)) return true
        var dot = host.indexOf('.')
        while (dot != -1) {
            if (blockedHosts.contains(host.substring(dot + 1))) return true
            dot = host.indexOf('.', dot + 1)
        }
        return false
    }

    private fun extractHost(url: String): String? {
        return try {
            URI(url).host?.lowercase()
        } catch (e: Exception) {
            val start = url.indexOf("://")
            if (start == -1) return null
            url.substring(start + 3).split("/", "?", "#", ":").firstOrNull()?.lowercase()
        }
    }

    private fun readAssetLines(ctx: Context, file: String, action: (String) -> Unit) {
        try {
            ctx.assets.open(file).use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine(action)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $file: ${e.message}")
        }
    }

    data class Stats(val hosts: Int, val patterns: Int, val cosmetic: Int)
    fun stats() = Stats(blockedHosts.size, urlPatterns.size, cosmeticRules.size)
}
