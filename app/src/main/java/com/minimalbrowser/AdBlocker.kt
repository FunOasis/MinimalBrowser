package com.minimalbrowser

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

class AdBlocker private constructor(private val context: Context) {

    private val blockedHosts   = HashSet<String>(100_000)
    private val urlPatterns    = ArrayList<String>(5_000)
    private val cosmeticRules  = ArrayList<String>(500)
    private val dynamicFilters = HashSet<String>(1_000)

    companion object {
        private const val TAG = "AdBlocker"
        @Volatile private var instance: AdBlocker? = null
        fun get(context: Context): AdBlocker =
            instance ?: synchronized(this) {
                instance ?: AdBlocker(context.applicationContext).also { instance = it }
            }

        // Force reload (call after filter update)
        fun reset() { instance = null }
    }

    init {
        loadHosts(context)
        loadPatterns(context)
        loadCosmetic(context)
        loadCustomFilters()
        Log.i(TAG, "Loaded ${blockedHosts.size} hosts, ${urlPatterns.size} patterns, ${cosmeticRules.size} cosmetic, ${dynamicFilters.size} dynamic")
    }

    private fun loadHosts(ctx: Context) {
        readAssetLines(ctx, "blocklist.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!')) {
                val parts = line.trim().split("\\s+".toRegex())
                val host = if (parts.size >= 2) parts[1] else parts[0]
                if (host.isNotBlank()) blockedHosts.add(host.lowercase())
            }
        }
    }

    private fun loadPatterns(ctx: Context) {
        readAssetLines(ctx, "filters.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!')) {
                urlPatterns.add(line.trim().lowercase())
            }
        }
    }

    private fun loadCosmetic(ctx: Context) {
        readAssetLines(ctx, "cosmetic.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!')) {
                cosmeticRules.add(line.trim())
            }
        }
    }

    private fun loadCustomFilters() {
        val custom = Prefs.get(context).customFilters
        custom.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith('#')) {
                if (trimmed.contains("/") || trimmed.contains("*")) {
                    dynamicFilters.add(trimmed.lowercase())
                } else {
                    blockedHosts.add(trimmed.lowercase())
                }
            }
        }
    }

    fun reloadCustomFilters() {
        dynamicFilters.clear()
        loadCustomFilters()
    }

    fun isBlocked(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        val host = extractHost(lower)
        if (host != null && isHostBlocked(host)) return true

        for (pattern in urlPatterns) {
            if (lower.contains(pattern)) return true
        }

        for (filter in dynamicFilters) {
            if (matchesDynamic(lower, filter)) return true
        }

        return false
    }

    private fun matchesDynamic(url: String, regex: Regex): Boolean {
    return regex.containsMatchIn(url)
}

    fun cosmeticCSS(): String {
        if (cosmeticRules.isEmpty()) return ""
        return cosmeticRules.joinToString(",\n") + " { display: none !important; }\n"
    }

    fun cosmeticScript(): String {
        val css = cosmeticCSS().replace("\"", "\\\"").replace("\n", "\\n")
        if (css.isBlank()) return ""
        return """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = "$css";
                if (document.head) document.head.appendChild(style);
            })();
        """.trimIndent()
    }

    private fun isHostBlocked(host: String): Boolean {
        if (blockedHosts.contains(host)) return true
        var dot = host.indexOf('.')
        while (dot != -1) {
            val parent = host.substring(dot + 1)
            if (blockedHosts.contains(parent)) return true
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
            val rest = url.substring(start + 3)
            rest.split("/", "?", "#", ":").firstOrNull()?.lowercase()
        }
    }

    private fun readAssetLines(ctx: Context, fileName: String, action: (String) -> Unit) {
        try {
            ctx.assets.open(fileName).use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine(action)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $fileName: ${e.message}")
        }
    }

    data class Stats(val hosts: Int, val patterns: Int, val cosmetic: Int)
    fun stats() = Stats(blockedHosts.size, urlPatterns.size, cosmeticRules.size)
}
