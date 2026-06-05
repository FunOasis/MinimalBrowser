package com.minimalbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BrowserTab(val id: String, val url: String, val title: String)

class TabManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tabs", Context.MODE_PRIVATE)

    fun saveTabs(tabs: List<BrowserTab>, activeId: String) {
        val arr = JSONArray()
        tabs.forEach { tab ->
            arr.put(JSONObject().apply {
                put("id",    tab.id)
                put("url",   tab.url)
                put("title", tab.title)
            })
        }
        prefs.edit()
            .putString("tabs",   arr.toString())
            .putString("active", activeId)
            .apply()
    }

    fun loadTabs(): List<BrowserTab> {
        val json = prefs.getString("tabs", null) ?: return emptyList()
        val list = mutableListOf<BrowserTab>()
        val arr  = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(BrowserTab(
                id    = obj.getString("id"),
                url   = obj.getString("url"),
                title = obj.getString("title")
            ))
        }
        return list
    }

    fun getActiveId(): String = prefs.getString("active", "") ?: ""
}
