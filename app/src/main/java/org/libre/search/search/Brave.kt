package org.libre.search.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.libre.search.core.Http
import org.libre.search.core.HttpException
import org.libre.search.core.Prefs
import org.libre.search.core.Text
import org.libre.search.core.arr
import org.libre.search.core.obj
import org.libre.search.core.objects
import org.libre.search.core.str
import org.libre.search.core.stripHtml

class MissingKeyException : Exception("Add your Brave Search API key in Settings to get web results.")

/**
 * Raw web results come from the Brave Search API. Brave only supplies the list of pages:
 * ranking tweaks, filtering and every rich panel are done by this app.
 * No AI summary is ever requested.
 */
object Brave {
    private const val BASE = "https://api.search.brave.com/res/v1"
    private val gate = Mutex()
    private var lastCall = 0L

    private fun headers(): Map<String, String> {
        val key = Prefs.braveKey
        if (key.isBlank()) throw MissingKeyException()
        return mapOf("X-Subscription-Token" to key, "Accept" to "application/json")
    }

    private fun langParams(): Map<String, String?> {
        val lang = Prefs.searchLang
        return mapOf(
            "country" to Prefs.country,
            "search_lang" to if (lang == "all") null else lang,
            "ui_lang" to if (lang == "en") "en-GB" else "fr-FR",
        )
    }

    /** The free plan allows about one request per second, so calls are spaced out. */
    private suspend fun call(url: String): JSONObject = gate.withLock {
        val wait = 1050 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) delay(wait)
        try {
            lastCall = System.currentTimeMillis()
            JSONObject(Http.getString(url, headers()))
        } catch (e: HttpException) {
            if (e.code == 429) {
                delay(1200)
                lastCall = System.currentTimeMillis()
                JSONObject(Http.getString(url, headers()))
            } else throw e
        }
    }

    suspend fun web(query: String, page: Int = 0, filter: String? = null): WebPage {
        val url = Http.url(
            "$BASE/web/search",
            mapOf(
                "q" to query,
                "count" to "20",
                "offset" to page.coerceIn(0, 9).toString(),
                "safesearch" to Prefs.safeSearch,
                "spellcheck" to "true",
                "text_decorations" to "false",
                "result_filter" to (filter ?: "web,videos,discussions,faq,query"),
            ) + langParams()
        )
        return parseWeb(call(url))
    }

    private fun parseWeb(j: JSONObject): WebPage {
        val results = j.obj("web").arr("results").objects().mapNotNull { r ->
            val url = r.str("url") ?: return@mapNotNull null
            val meta = r.obj("meta_url")
            val product = r.obj("product")
            val price = product.str("price")
                ?: product.arr("offers").objects().firstOrNull()?.let { o ->
                    val p = o.str("price")
                    if (p != null) "$p ${o.str("priceCurrency") ?: ""}".trim() else null
                }
            val rating = product.obj("rating").str("ratingValue") ?: r.obj("rating").str("ratingValue")
            WebResult(
                title = (r.str("title") ?: url).stripHtml(),
                url = url,
                description = (r.str("description") ?: "").stripHtml(),
                siteName = r.obj("profile").str("name") ?: meta.str("hostname")?.removePrefix("www.") ?: Text.host(url),
                favicon = meta.str("favicon") ?: r.obj("profile").str("img"),
                thumbnail = r.obj("thumbnail").str("src"),
                age = r.str("age"),
                extraSnippets = r.arr("extra_snippets")?.let { a -> (0 until a.length()).map { a.optString(it).stripHtml() } } ?: emptyList(),
                sitelinks = r.obj("deep_results").arr("buttons").objects().mapNotNull { b ->
                    val t = b.str("title"); val u = b.str("url")
                    if (t != null && u != null) t.stripHtml() to u else null
                }.take(6),
                price = price,
                rating = rating,
            )
        }
        val videos = j.obj("videos").arr("results").objects().mapNotNull { parseVideo(it) }
        val discussions = j.obj("discussions").arr("results").objects().mapNotNull { r ->
            val url = r.str("url") ?: return@mapNotNull null
            val d = r.obj("data")
            Discussion(
                title = (d.str("title") ?: r.str("title") ?: url).stripHtml(),
                url = url,
                forum = d.str("forum_name") ?: Text.host(url),
                snippet = (d.str("top_comment") ?: d.str("question") ?: r.str("description") ?: "").stripHtml(),
                answers = d.str("num_answers"),
                score = d.str("score"),
                age = r.str("age"),
            )
        }
        val faq = j.obj("faq").arr("results").objects().mapNotNull { r ->
            val q = r.str("question") ?: return@mapNotNull null
            Faq(q.stripHtml(), (r.str("answer") ?: "").stripHtml(), r.str("url") ?: "", (r.str("title") ?: "").stripHtml())
        }
        val products = results.filter { it.price != null }.map {
            Product(it.title, it.url, it.thumbnail, it.price, it.siteName, it.rating)
        }
        val q = j.obj("query")
        return WebPage(
            results = results,
            videos = videos,
            discussions = discussions,
            faq = faq,
            products = products,
            alteredQuery = q.str("altered"),
            moreAvailable = q?.optBoolean("more_results_available", results.size >= 15) ?: false,
        )
    }

    private fun parseVideo(r: JSONObject): VideoResult? {
        val url = r.str("url") ?: return null
        val v = r.obj("video")
        return VideoResult(
            title = (r.str("title") ?: url).stripHtml(),
            url = url,
            thumbnail = r.obj("thumbnail").str("src") ?: v.obj("thumbnail").str("src"),
            duration = v.str("duration"),
            creator = v.str("creator") ?: v.obj("author").str("name"),
            publisher = v.str("publisher") ?: r.obj("meta_url").str("hostname")?.removePrefix("www."),
            age = r.str("age"),
        )
    }

    suspend fun videos(query: String, page: Int = 0): List<VideoResult> {
        val url = Http.url(
            "$BASE/videos/search",
            mapOf(
                "q" to query, "count" to "40", "offset" to page.coerceIn(0, 9).toString(),
                "safesearch" to Prefs.safeSearch, "spellcheck" to "true",
            ) + langParams()
        )
        return call(url).arr("results").objects().mapNotNull { parseVideo(it) }
    }

    suspend fun images(query: String): List<ImageResult> {
        val url = Http.url(
            "$BASE/images/search",
            mapOf(
                "q" to query, "count" to "100",
                "safesearch" to if (Prefs.safeSearch == "off") "off" else "strict",
                "spellcheck" to "true",
                "country" to Prefs.country,
                "search_lang" to if (Prefs.searchLang == "all") null else Prefs.searchLang,
            )
        )
        return call(url).arr("results").objects().mapNotNull { r ->
            val page = r.str("url") ?: return@mapNotNull null
            val props = r.obj("properties")
            val full = props.str("url") ?: return@mapNotNull null
            val thumb = r.obj("thumbnail").str("src") ?: full
            ImageResult(
                title = (r.str("title") ?: "").stripHtml(),
                pageUrl = page,
                imageUrl = full,
                thumbnail = thumb,
                source = r.str("source") ?: Text.host(page),
                width = props?.optInt("width") ?: 0,
                height = props?.optInt("height") ?: 0,
            )
        }
    }

    /** Autocomplete. Needs Brave's autosuggest plan; returns null when the key doesn't cover it. */
    suspend fun suggest(query: String): List<String>? {
        if (Prefs.suggestUnavailable || Prefs.braveKey.isBlank()) return null
        val url = Http.url(
            "$BASE/suggest/search",
            mapOf("q" to query, "count" to "8", "country" to Prefs.country)
        )
        return try {
            val j = JSONObject(Http.getString(url, headers()))
            j.arr("results").objects().mapNotNull { it.str("query") }
        } catch (e: HttpException) {
            if (e.code == 401 || e.code == 403 || e.code == 422) Prefs.suggestUnavailable = true
            null
        } catch (_: Exception) {
            null
        }
    }
}
