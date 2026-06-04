package com.minimalbrowser

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

/**
 * AdBlocker — uBlock Origin-style blocking engine.
 *
 * Blocking strategy (in priority order):
 *  1. Exact domain match against blocklist
 *  2. Subdomain match  (e.g. "ads.example.com" hits rule "example.com")
 *  3. Keyword/pattern match against URL
 *  4. Cosmetic (element-hiding) CSS injected into every page
 *
 * Rule sources (bundled in assets/):
 *  - blocklist.txt  — one host per line, comment lines start with '#'
 *  - filters.txt    — uBlock-style URL keyword patterns, one per line
 *  - cosmetic.txt   — CSS selector rules for element hiding
 */
class AdBlocker private constructor(context: Context) {

    private val blockedHosts   = HashSet<String>(50_000)
    private val urlPatterns    = ArrayList<String>(2_000)
    private val cosmeticRules  = ArrayList<String>(500)

    companion object {
        private const val TAG = "AdBlocker"

        @Volatile private var instance: AdBlocker? = null

        fun get(context: Context): AdBlocker =
            instance ?: synchronized(this) {
                instance ?: AdBlocker(context.applicationContext).also { instance = it }
            }
    }

    init {
        loadHosts(context)
        loadPatterns(context)
        loadCosmetic(context)
        Log.i(TAG, "Loaded ${blockedHosts.size} hosts, ${urlPatterns.size} patterns, ${cosmeticRules.size} cosmetic rules")
    }

    // ── Loaders ──────────────────────────────────────────────────────────────

    private fun loadHosts(ctx: Context) {
        readAssetLines(ctx, "blocklist.txt") { line ->
            if (line.isNotBlank() && !line.startsWith('#') && !line.startsWith('!')) {
                // Support "0.0.0.0 hostname" and "127.0.0.1 hostname" formats
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

    private fun readAssetLines(ctx: Context, fileName: String, action: (String) -> Unit) {
        try {
            ctx.assets.open(fileName).use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine(action)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $fileName: ${e.message}")
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if the URL should be blocked. */
    fun isBlocked(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        // 1. Host-based blocking
        val host = extractHost(lower)
        if (host != null && isHostBlocked(host)) return true

        // 2. Keyword / pattern matching
        for (pattern in urlPatterns) {
            if (lower.contains(pattern)) return true
        }

        return false
    }

    /** Returns CSS to inject for cosmetic filtering (element hiding). */
    fun cosmeticCSS(): String {
        if (cosmeticRules.isEmpty()) return ""
        return cosmeticRules.joinToString(",\n") + " { display: none !important; }\n"
    }

    /** JavaScript snippet that injects cosmetic CSS into a page. */
    fun cosmeticScript(): String {
        val css = cosmeticCSS().replace("\"", "\\\"").replace("\n", "\\n")
        if (css.isBlank()) return ""
        return """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = "$css";
                document.head.appendChild(style);
            })();
        """.trimIndent()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isHostBlocked(host: String): Boolean {
        if (blockedHosts.contains(host)) return true
        // Check parent domains (subdomain walk)
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
            // Fallback: grab text between "://" and next "/"
            val start = url.indexOf("://")
            if (start == -1) return null
            val rest = url.substring(start + 3)
            rest.split("/", "?", "#", ":").firstOrNull()?.lowercase()
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    data class Stats(val hosts: Int, val patterns: Int, val cosmetic: Int)

    fun stats() = Stats(blockedHosts.size, urlPatterns.size, cosmeticRules.size)
}
