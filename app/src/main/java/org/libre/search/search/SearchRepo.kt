package org.libre.search.search

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.libre.search.core.LocationHelper
import org.libre.search.core.Place
import org.libre.search.core.Prefs
import org.libre.search.news.Article
import org.libre.search.news.NewsRepo
import org.libre.search.panels.Business
import org.libre.search.panels.Places
import org.libre.search.panels.Tmdb
import org.libre.search.panels.TmdbHit
import org.libre.search.panels.Weather
import org.libre.search.panels.WeatherData
import org.libre.search.panels.Wiki
import org.libre.search.panels.WikiPanel

data class AllResults(
    val query: String,
    val web: WebPage? = null,
    val webError: String? = null,
    val calculator: Calculator.Answer? = null,
    val weather: WeatherData? = null,
    val screen: TmdbHit? = null,
    val screenWikiUrl: String? = null,
    val wiki: WikiPanel? = null,
    val places: List<Business> = emptyList(),
    val here: Place? = null,
    val topStories: List<Article> = emptyList(),
)

object SearchRepo {

    suspend fun all(ctx: Context, query: String, page: Int): AllResults = coroutineScope {
        if (page > 0) {
            val w = runCatching { Brave.web(query, page) }
            return@coroutineScope AllResults(
                query = query,
                web = w.getOrNull()?.let { clean(it) },
                webError = w.exceptionOrNull()?.let { errorText(it) },
            )
        }
        val calc = Calculator.evaluate(query)
        val weatherPlace = QueryIntent.weatherPlace(query)
        val local = QueryIntent.local(query)

        val hereAsync = async { runCatching { LocationHelper.current(ctx) }.getOrNull() }
        val webAsync = async { runCatching { Brave.web(query) } }
        val entity = QueryIntent.entityCandidate(query) && calc == null && weatherPlace == null && local == null
        val tmdbAsync = async { if (entity) runCatching { Tmdb.lookup(query) }.getOrNull() else null }
        val wikiAsync = async { if (entity) runCatching { Wiki.lookup(query) }.getOrNull() else null }
        val newsAsync = async {
            runCatching {
                NewsRepo.ensureFresh(60 * 60 * 1000)
                NewsRepo.search(query).take(6)
            }.getOrDefault(emptyList())
        }

        val here = hereAsync.await()
        val weatherAsync = async {
            if (weatherPlace == null) null else runCatching {
                val place = if (weatherPlace.isBlank()) here else LocationHelper.geocode(weatherPlace)
                place?.let { Weather.fetch(it) }
            }.getOrNull()
        }
        val placesAsync = async {
            if (local == null) emptyList() else runCatching { Places.search(query, here) }.getOrDefault(emptyList())
        }

        val tmdb = tmdbAsync.await()
        val screenWiki = when (tmdb) {
            is TmdbHit.Work -> tmdb.work.wikidataId?.let { Wiki.articleForWikidata(it) }
                ?: Wiki.articleForTitle(tmdb.work.title + if (tmdb.work.isTv) "" else " (film)")
            is TmdbHit.Person -> tmdb.person.wikidataId?.let { Wiki.articleForWikidata(it) }
                ?: Wiki.articleForTitle(tmdb.person.name)
            null -> null
        }
        val web = webAsync.await()
        AllResults(
            query = query,
            web = web.getOrNull()?.let { clean(it) },
            webError = web.exceptionOrNull()?.let { errorText(it) },
            calculator = calc,
            weather = weatherAsync.await(),
            screen = tmdb,
            screenWikiUrl = screenWiki,
            wiki = if (tmdb == null) wikiAsync.await() else null,
            places = placesAsync.await(),
            here = here,
            topStories = newsAsync.await(),
        )
    }

    private fun clean(p: WebPage) = p.copy(
        results = ResultFilter.web(p.results),
        videos = ResultFilter.videos(p.videos),
        discussions = ResultFilter.discussions(p.discussions),
        products = p.products.filterNot { ResultFilter.blocked(it.url) },
    )

    fun errorText(e: Throwable): String = when (e) {
        is MissingKeyException -> e.message ?: "Missing API key"
        is org.libre.search.core.HttpException -> when (e.code) {
            401, 403 -> "Your Brave API key was refused. Check it in Settings."
            422 -> "Brave rejected the request (check your API key and plan)."
            429 -> "Brave's monthly or per-second limit was reached. Try again in a moment."
            else -> "Search service error (${e.code})."
        }
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "The search took too long. Try again."
        else -> e.message ?: "Something went wrong."
    }

    suspend fun forums(query: String, page: Int): WebPage {
        val a = Brave.web(query, page, "discussions,web")
        val forumHosts = listOf("reddit.com", "lemmy", "stackexchange.com", "stackoverflow.com", "quora.com",
            "forum", "forums", "community", "discourse", "hardware.fr", "jeuxvideo.com", "commentcamarche",
            "tripadvisor", "hackernews", "news.ycombinator.com", "mastodon", "raddle.me", "kbin", "discuss")
        val threads = a.results.filter { r -> forumHosts.any { r.url.contains(it, true) } }
        return clean(a.copy(results = threads))
    }

    suspend fun shopping(query: String, page: Int): WebPage {
        val q = if (QueryIntent.shoppingLike(query)) query else "$query ${if (Prefs.searchLang == "en") "buy price" else "prix acheter"}"
        return clean(Brave.web(q, page, "web"))
    }
}
