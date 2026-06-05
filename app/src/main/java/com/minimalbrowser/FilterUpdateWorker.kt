package com.minimalbrowser

import android.content.Context
import android.util.Log
import androidx.work.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class FilterUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "FilterUpdateWorker"

        // uBlock Origin default filter list URLs (no porn lists)
        private val FILTER_URLS = listOf(
            "https://easylist.to/easylist/easylist.txt",
            "https://easylist.to/easylist/easyprivacy.txt",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
            "https://raw.githubusercontent.com/nicktacular/pgl-yoyo-ublock/refs/heads/master/pgl-yoyo.txt"
        )

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "filter_update",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override fun doWork(): Result {
        return try {
            val file = File(context.filesDir, "blocklist_dynamic.txt")
            val sb = StringBuilder()
            sb.appendLine("# Auto-updated filter lists")
            sb.appendLine("# Last updated: ${System.currentTimeMillis()}")

            FILTER_URLS.forEach { urlStr ->
                try {
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout    = 30_000
                    conn.connect()
                    if (conn.responseCode == 200) {
                        conn.inputStream.bufferedReader().forEachLine { line ->
                            sb.appendLine(line)
                        }
                        Log.i(TAG, "Updated from $urlStr")
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch $urlStr: ${e.message}")
                }
            }

            file.writeText(sb.toString())
            AdBlocker.reset()
            Log.i(TAG, "Filter update complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Filter update failed: ${e.message}")
            Result.retry()
        }
    }
}
