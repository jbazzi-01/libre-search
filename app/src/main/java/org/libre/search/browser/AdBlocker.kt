package org.libre.search.browser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libre.search.core.Http
import org.libre.search.core.Prefs
import java.io.File

/**
 * Network-level ad, tracker and cookie-banner blocking for the built-in browser.
 * Uses a bundled base list plus the public StevenBlack, EasyList, EasyPrivacy, Liste FR
 * and Fanboy cookie lists (domain rules), refreshed weekly on the phone.
 */
object AdBlocker {
    @Volatile private var hosts: Set<String> = emptySet()
    @Volatile var cosmeticCss: String = ""
        private set
    private lateinit var dir: File
    private lateinit var appContext: Context

    val lists = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://easylist-downloads.adblockplus.org/liste_fr.txt",
        "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt",
        "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
    )

    /** Never block these, even if a list mentions them (keeps the app's own services working). */
    private val allow = setOf(
        "api.search.brave.com", "imgs.search.brave.com", "api.themoviedb.org", "image.tmdb.org",
        "wikipedia.org", "wikimedia.org", "wikidata.org", "api.open-meteo.com", "openstreetmap.org",
        "googlevideo.com", "youtube.com", "ytimg.com", "youtube-nocookie.com",
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        dir = File(context.filesDir, "adblock").apply { mkdirs() }
        Thread { load() }.start()
    }

    private fun load() {
        val set = HashSet<String>(200_000)
        try {
            appContext.assets.open("adblock_base.txt").bufferedReader().useLines { lines ->
                lines.forEach { l -> val t = l.trim(); if (t.isNotEmpty() && !t.startsWith("#")) set += t.lowercase() }
            }
        } catch (_: Exception) {}
        val cssParts = StringBuilder()
        try {
            cssParts.append(appContext.assets.open("cosmetic.css").bufferedReader().readText())
        } catch (_: Exception) {}
        dir.listFiles()?.filter { it.name.endsWith(".hosts") }?.forEach { f ->
            try { f.forEachLine { l -> if (l.isNotBlank()) set += l.trim() } } catch (_: Exception) {}
        }
        try {
            val extra = File(dir, "generic_cosmetic.css")
            if (extra.exists()) cssParts.append('\n').append(extra.readText())
        } catch (_: Exception) {}
        allow.forEach { set.remove(it) }
        hosts = set
        cosmeticCss = cssParts.toString()
    }

    fun blockedCount() = hosts.size

    fun isBlocked(host: String?): Boolean {
        if (host == null || !Prefs.adBlock) return false
        var h = host.lowercase()
        if (allow.any { h == it || h.endsWith(".$it") }) return false
        val set = hosts
        while (true) {
            if (set.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0 || i == h.lastIndexOf('.')) return false
            h = h.substring(i + 1)
        }
    }

    fun needsUpdate() = System.currentTimeMillis() - Prefs.adListsUpdated > 7L * 24 * 3600 * 1000

    /** Downloads and compiles the filter lists. Returns the number of blocked domains. */
    suspend fun update(): Int = withContext(Dispatchers.IO) {
        val genericCss = LinkedHashSet<String>()
        lists.forEachIndexed { idx, url ->
            try {
                val text = Http.getString(url, emptyMap(), Http.BROWSER_UA)
                val domains = HashSet<String>()
                text.lineSequence().forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
                    when {
                        line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") -> {
                            val d = line.split(Regex("\\s+")).getOrNull(1)?.substringBefore('#')?.trim()
                            if (d != null && d.contains('.') && d != "0.0.0.0" && d != "localhost") domains += d.lowercase()
                        }
                        line.startsWith("||") -> {
                            // Only plain domain rules: ||example.com^ or ||example.com^$third-party
                            val m = Regex("^\\|\\|([a-z0-9.-]+)\\^(\\$(third-party|3p|all|script|image|popup)(,[a-z-]+)*)?$").find(line)
                            if (m != null) domains += m.groupValues[1]
                        }
                        line.startsWith("##") && idx >= 3 -> {
                            val sel = line.substring(2)
                            if (sel.length < 120 && !sel.contains(":-abp") && !sel.contains(":has-text") && !sel.contains(":upward")
                                && !sel.contains(":style") && !sel.contains(":remove") && !sel.contains(":xpath")
                            ) genericCss += sel
                        }
                    }
                }
                if (domains.isNotEmpty()) File(dir, "list$idx.hosts").writeText(domains.joinToString("\n"))
            } catch (_: Exception) {
            }
        }
        if (genericCss.isNotEmpty()) {
            // One rule per selector: an invalid selector then only drops its own rule.
            val css = genericCss.take(5000).joinToString("\n") { "$it{display:none!important}" }
            File(dir, "generic_cosmetic.css").writeText(css)
        }
        Prefs.adListsUpdated = System.currentTimeMillis()
        load()
        hosts.size
    }
}
