package org.libre.search.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

object Prefs {
    private lateinit var sp: SharedPreferences

    /** Bumped on every change so Compose screens can observe settings. */
    val version = mutableIntStateOf(0)

    fun init(context: Context) {
        sp = context.getSharedPreferences("libre_prefs", Context.MODE_PRIVATE)
    }

    private fun s(key: String, def: String) = sp.getString(key, def) ?: def
    private fun setS(key: String, v: String) { sp.edit().putString(key, v).apply(); version.intValue++ }
    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setB(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply(); version.intValue++ }

    var braveKey: String
        get() = s("brave_key", "").trim()
        set(v) = setS("brave_key", v.trim())

    var tmdbKey: String
        get() = s("tmdb_key", "").trim()
        set(v) = setS("tmdb_key", v.trim())

    /** Country code for web results (Brave "country"). */
    var country: String
        get() = s("country", "FR")
        set(v) = setS("country", v)

    /** "fr", "en" or "all" */
    var searchLang: String
        get() = s("search_lang", "fr")
        set(v) = setS("search_lang", v)

    /** Language used for Wikipedia and TMDB panels. */
    var panelLang: String
        get() = s("panel_lang", "fr")
        set(v) = setS("panel_lang", v)

    /** "off", "moderate", "strict" */
    var safeSearch: String
        get() = s("safe", "moderate")
        set(v) = setS("safe", v)

    /** "system", "dark", "light" */
    var theme: String
        get() = s("theme", "dark")
        set(v) = setS("theme", v)

    var builtInBrowser: Boolean
        get() = b("builtin_browser", true)
        set(v) = setB("builtin_browser", v)

    var saveHistory: Boolean
        get() = b("save_history", true)
        set(v) = setB("save_history", v)

    var adBlock: Boolean
        get() = b("adblock", true)
        set(v) = setB("adblock", v)

    var cookieBanners: Boolean
        get() = b("cookie_banners", true)
        set(v) = setB("cookie_banners", v)

    var videoPlayer: Boolean
        get() = b("video_player", true)
        set(v) = setB("video_player", v)

    var hideAiImages: Boolean
        get() = b("hide_ai_images", true)
        set(v) = setB("hide_ai_images", v)

    var hideIsraeliOutlets: Boolean
        get() = b("hide_il_outlets", true)
        set(v) = setB("hide_il_outlets", v)

    /** "gps" or "manual" */
    var locationMode: String
        get() = s("loc_mode", "gps")
        set(v) = setS("loc_mode", v)

    var manualCity: String
        get() = s("manual_city", "")
        set(v) = setS("manual_city", v)

    var manualLat: Double
        get() = s("manual_lat", "").toDoubleOrNull() ?: Double.NaN
        set(v) = setS("manual_lat", v.toString())

    var manualLon: Double
        get() = s("manual_lon", "").toDoubleOrNull() ?: Double.NaN
        set(v) = setS("manual_lon", v.toString())

    var lastCity: String
        get() = s("last_city", "")
        set(v) = setS("last_city", v)

    var lastLat: Double
        get() = s("last_lat", "").toDoubleOrNull() ?: Double.NaN
        set(v) = setS("last_lat", v.toString())

    var lastLon: Double
        get() = s("last_lon", "").toDoubleOrNull() ?: Double.NaN
        set(v) = setS("last_lon", v.toString())

    var blockedDomains: Set<String>
        get() = sp.getStringSet("blocked_domains", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("blocked_domains", v).apply(); version.intValue++ }

    var disabledFeeds: Set<String>
        get() = sp.getStringSet("disabled_feeds", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("disabled_feeds", v).apply(); version.intValue++ }

    /** JSON array of user-added sources. */
    var customFeedsJson: String
        get() = s("custom_feeds", "[]")
        set(v) = setS("custom_feeds", v)

    var adListsUpdated: Long
        get() = sp.getLong("adlists_updated", 0L)
        set(v) { sp.edit().putLong("adlists_updated", v).apply(); version.intValue++ }

    var suggestUnavailable: Boolean
        get() = b("suggest_unavailable", false)
        set(v) = setB("suggest_unavailable", v)
}
