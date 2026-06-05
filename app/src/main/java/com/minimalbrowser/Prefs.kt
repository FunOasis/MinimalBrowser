package com.minimalbrowser

import android.content.Context
import android.content.SharedPreferences

class Prefs private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val NAME = "minimal_browser_prefs"
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(
                    context.applicationContext
                        .getSharedPreferences(NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }

    var homepage: String
        get() = prefs.getString("homepage", "prs://home") ?: "prs://home"
        set(v) = prefs.edit().putString("homepage", v).apply()

    var jsEnabled: Boolean
        get() = prefs.getBoolean("js_enabled", true)
        set(v) = prefs.edit().putBoolean("js_enabled", v).apply()

    var adblockEnabled: Boolean
        get() = prefs.getBoolean("adblock_enabled", true)
        set(v) = prefs.edit().putBoolean("adblock_enabled", v).apply()

    var customScript: String
        get() = prefs.getString("custom_script", "") ?: ""
        set(v) = prefs.edit().putString("custom_script", v).apply()

    var customFilters: String
        get() = prefs.getString("custom_filters", "") ?: ""
        set(v) = prefs.edit().putString("custom_filters", v).apply()
}
