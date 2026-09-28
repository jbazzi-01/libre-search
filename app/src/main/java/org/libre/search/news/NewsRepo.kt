package org.libre.search.news

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.libre.search.LibreApp
import org.libre.search.core.Text
import java.io.File

object NewsRepo {
    val articles = mutableStateListOf<Article>()
    val failing = mutableStateListOf<String>()
    val loading = mutableStateOf(false)
    private var lastRefresh = 0L
    private val lock = Mutex()
    private val workingFeed = HashMap<String, String>()
    private var loadedFromDisk = false

    private fun cacheFile() = File(LibreApp.instance.cacheDir, "news_cache.json")
    private fun feedMapFile() = File(LibreApp.instance.filesDir, "feed_urls.json")

    fun source(id: String): Source? = Sources.all().firstOrNull { it.id == id }

    private fun loadDisk() {
        if (loadedFromDisk) return
        loadedFromDisk = true
        try {
            val m = JSONObject(feedMapFile().readText())
            m.keys().forEach { k -> workingFeed[k] = m.getString(k) }
        } catch (_: Exception) {}
        try {
            val a = JSONArray(cacheFile().readText())
            val list = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Article(o.getString("s"), o.getString("t"), o.getString("u"), o.optString("d"),
                    o.optString("i").ifBlank { null }, o.optLong("w"), o.optString("a").ifBlank { null })
            }
            if (articles.isEmpty()) articles.addAll(list)
        } catch (_: Exception) {}
    }

    private fun saveDisk() {
        try {
            val a = JSONArray()
            articles.take(1500).forEach {
                a.put(JSONObject().put("s", it.sourceId).put("t", it.title).put("u", it.url).put("d", it.summary)
                    .put("i", it.image ?: "").put("w", it.time).put("a", it.author ?: ""))
            }
            cacheFile().writeText(a.toString())
            val m = JSONObject()
            workingFeed.forEach { (k, v) -> m.put(k, v) }
            feedMapFile().writeText(m.toString())
        } catch (_: Exception) {}
    }

    suspend fun ensureFresh(maxAgeMs: Long = 20 * 60 * 1000) {
        withContext(Dispatchers.IO) { loadDisk() }
        if (System.currentTimeMillis() - lastRefresh < maxAgeMs && articles.isNotEmpty()) return
        refresh()
    }

    suspend fun refresh() = lock.withLock {
        loading.value = true
        try {
            val sources = Sources.enabled()
            val sem = Semaphore(6)
            val results = coroutineScope {
                sources.map { src ->
                    async(Dispatchers.IO) { sem.withPermit { src.id to loadSource(src) } }
                }.awaitAll()
            }
            val fresh = results.flatMap { it.second ?: emptyList() }
            val failedIds = results.filter { it.second == null }.map { it.first }
            // Keep older cached articles for sources that failed this time.
            val kept = articles.filter { a -> a.sourceId in failedIds }
            val merged = (fresh + kept)
                .distinctBy { it.url }
                .filter { a -> sources.any { it.id == a.sourceId } }
                .sortedByDescending { it.time }
            withContext(Dispatchers.Main) {
                articles.clear(); articles.addAll(merged)
                failing.clear(); failing.addAll(failedIds)
            }
            lastRefresh = System.currentTimeMillis()
            withContext(Dispatchers.IO) { saveDisk() }
        } finally {
            loading.value = false
        }
    }

    /** Tries the remembered feed, then the known candidates, then autodiscovery. */
    private suspend fun loadSource(src: Source): List<Article>? {
        val candidates = LinkedHashSet<String>()
        workingFeed[src.id]?.let { candidates += it }
        candidates += src.feeds
        for (url in candidates) {
            val list = tryFeed(url, src) ?: continue
            workingFeed[src.id] = url
            return list
        }
        if (src.site.isNotBlank()) {
            for (url in Rss.discover(src.site)) {
                if (url in candidates) continue
                val list = tryFeed(url, src) ?: continue
                workingFeed[src.id] = url
                return list
            }
        }
        return null
    }

    private suspend fun tryFeed(url: String, src: Source): List<Article>? = try {
        val body = Rss.fetch(url)
        if (!Rss.looksLikeFeed(body)) null
        else Rss.parse(body, src.id, src.site.ifBlank { url }).takeIf { it.isNotEmpty() }?.take(40)
    } catch (_: Exception) {
        null
    }

    /** Validates a feed (or a site with a discoverable feed) before the user adds it. */
    suspend fun probe(input: String): Pair<String, String>? {
        val url = if (input.startsWith("http")) input else "https://$input"
        try {
            val body = Rss.fetch(url)
            if (Rss.looksLikeFeed(body)) {
                val title = Regex("<title[^>]*>(?:<!\\[CDATA\\[)?([^<\\]]+)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim()
                return url to (title ?: Text.host(url))
            }
        } catch (_: Exception) {}
        for (f in Rss.discover(url)) {
            try {
                val body = Rss.fetch(f)
                if (Rss.looksLikeFeed(body)) {
                    val title = Regex("<title[^>]*>(?:<!\\[CDATA\\[)?([^<\\]]+)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim()
                    return f to (title ?: Text.host(url))
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ---------- Good news ----------

    private val positive = listOf(
        "victoire", "victorieu", "gagné", "gagnent", "gagne ", "remporte", "obtiennent", "obtenu", "arrach",
        "succès", "réussi", "abandon du projet", "abandonne", "recule", "renonce", "suspendu", "annulation du projet",
        "augmentation de salaire", "hausse de salaire", "réintégr", "relaxé", "relaxe", "acquitté", "libéré", "libération de",
        "enfin", "historique", "première", "solidarité", "entraide", "coopérative", "autogest", "reconnu", "régularis",
        "win ", "wins", "won ", "victory", "victorious", "secured", "secure a", "unionize", "unionise", "union recogni",
        "reinstated", "acquitted", "freed", "released from", "scrapped", "cancelled", "canceled", "blocked", "halted",
        "overturned", "landmark", "breakthrough", "first-ever", "celebrat", "success", "restored", "protected",
        "mutual aid", "cooperative", "co-op", "rewild", "recovery", "record low", "record high for", "banned",
    )

    private val negative = listOf(
        "killed", "kills", "dead", "death", "died", "massacre", "genocide", "bomb", "war crime", "shot ", "shooting",
        "murder", "attack", "tués", "tué ", "mort", "morts", "génocide", "bombard", "meurtre", "attaque", "blessé",
        "famine", "starv", "raid", "arrested", "arrêté", "expuls", "deport", "fascis", "far-right", "extrême droite",
        "torture", "abuse", "violence", "viol", "rape", "crackdown", "répression", "repression", "evict",
    )

    fun goodScore(a: Article): Int {
        val src = source(a.sourceId)
        if (src?.tendency == Tendency.GOOD) return 10
        val t = (a.title + " " + a.summary.take(200)).lowercase()
        val pos = positive.count { t.contains(it) }
        val neg = negative.count { t.contains(it) }
        return if (neg > 0) 0 else pos
    }

    fun goodNews(): List<Article> = articles.filter { goodScore(it) >= 2 || (goodScore(it) >= 1 && source(it.sourceId)?.tendency != Tendency.GOOD && titleWin(it)) }

    private fun titleWin(a: Article): Boolean {
        val t = a.title.lowercase()
        return listOf("victoire", "win", "won", "victory", "gagné", "remporte", "obtiennent", "acquitt", "relax", "scrapped", "abandon").any { t.contains(it) }
    }

    /** Articles matching a query, for the News tab and the Top stories box. */
    fun search(query: String): List<Article> {
        val words = Text.normalize(query).split(' ').filter { it.length > 2 }
        if (words.isEmpty()) return emptyList()
        return articles.map { a ->
            val t = Text.normalize(a.title)
            val s = Text.normalize(a.summary)
            val score = words.sumOf { w -> (if (t.contains(w)) 3 else 0) + (if (s.contains(w)) 1 else 0).toInt() }
            val all = words.all { w -> t.contains(w) || s.contains(w) }
            a to (if (all) score + 2 else if (words.size == 1) score else 0)
        }.filter { it.second >= 3 }
            .sortedWith(compareByDescending<Pair<Article, Int>> { it.second }.thenByDescending { it.first.time })
            .map { it.first }
    }

    @Suppress("unused")
    fun context(): Context = LibreApp.instance
}
