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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class AdBlocker private constructor(private val context: Context) {

    companion object {
        private const val TAG              = "AdBlocker"
        private const val CACHE_SIZE       = 4_000

        @Volatile private var instance: AdBlocker? = null

        fun get(context: Context): AdBlocker =
            instance ?: synchronized(this) {
                instance ?: AdBlocker(context.applicationContext).also { instance = it }
            }

        fun reset() { instance = null }
    }

    // Thread-safe Concurrent Collection Sets
    private val blockedHosts    = ConcurrentHashMap.newKeySet<String>(50_000)
    private val urlPatterns     = CopyOnWriteArrayList<String>()
    private val cosmeticRules   = CopyOnWriteArrayList<String>()
    private val dynamicFilters  = CopyOnWriteArrayList<Regex>()

    // Localized Thread-Safe Memory Cache
    private val decisionCache   = LruCache<String, Boolean>(CACHE_SIZE)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            loadHosts(context)
            loadPatterns(context)
            loadCosmetic(context)
            loadCustomFilters()
            Log.i(TAG, "Loaded structurally optimized rulesets.")
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
            val t = line.trim().lowercase()
            if (t.isNotBlank() && !t.startsWith('#') && !t.startsWith('!')) {
                urlPatterns.add(t)
            }
        }
    }

    private fun loadCosmetic(ctx: Context) {
        readAssetLines(ctx, "cosmetic.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!'))
                cosmeticRules.add(line.trim())
        }
    }

    private fun loadCustomFilters() {
        val prefs = Prefs.get(context).customFilters
        if (prefs.isBlank()) return
        prefs.lines().forEach { line ->
            val t = line.trim()
            if (t.isBlank() || t.startsWith('#')) return@forEach
            if (t.contains("/") || t.contains("*")) {
                try {
                    val pattern = t.replace(".", "\\.").replace("*", ".*")
                    dynamicFilters.add(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid filter: $t")
                }
            } else {
                blockedHosts.add(t.lowercase())
            }
        }
    }

    fun reloadCustomFilters() {
        dynamicFilters.clear()
        synchronized(decisionCache) {
            decisionCache.evictAll()
        }
        loadCustomFilters()
    }

    fun isBlocked(url: String): Boolean {
        if (url.isBlank()) return false

        // Cache reads must be thread-isolated cleanly
        synchronized(decisionCache) {
            decisionCache.get(url)?.let { return it }
        }

        val lower = url.lowercase()
        val result = checkBlocked(lower)

        synchronized(decisionCache) {
            decisionCache.put(url, result)
        }
        return result
    }

    private fun checkBlocked(lower: String): Boolean {
        val host = extractHost(lower)
        if (host != null && isHostBlocked(host)) return true

        // Fast match substring checks
        for (i in 0 until urlPatterns.size) {
            val pattern = urlPatterns.getOrNull(i) ?: break
            if (lower.contains(pattern)) return true
        }

        // Fast match dynamic token checks
        for (i in 0 until dynamicFilters.size) {
            val regex = dynamicFilters.getOrNull(i) ?: break
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
                if (window.prsCosmeticApplied) return;
                window.prsCosmeticApplied = true;
                var s = document.createElement('style');
                s.type = 'text/css';
                s.innerHTML = "$css";
                if (document.head) {
                    document.head.appendChild(s);
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        if (document.head) document.head.appendChild(s);
                    });
                }
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
            val uri = URI(url)
            uri.host?.lowercase() ?: extractFallbackHost(url)
        } catch (e: Exception) {
            extractFallbackHost(url)
        }
    }

    private fun extractFallbackHost(url: String): String? {
        val start = url.indexOf("://")
        if (start == -1) return null
        return url.substring(start + 3).split("/", "?", "#", ":").firstOrNull()?.lowercase()
    }

    private fun readAssetLines(ctx: Context, file: String, action: (String) -> Unit) {
        try {
            ctx.assets.open(file).use { stream ->
                BufferedReader(InputStreamReader(stream), 16384).use { reader ->
                    reader.forEachLine(action)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $file: ${e.message}")
        }
    }

    data class Stats(val hosts: Int, val patterns: Int, val cosmetic: Int)
    fun stats() = Stats(blockedHosts.size, urlPatterns.size, cosmeticRules.size)
}
