package org.libre.search.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.libre.search.core.LocationHelper
import org.libre.search.core.Place
import org.libre.search.panels.Business
import org.libre.search.panels.Places
import org.libre.search.search.AllResults
import org.libre.search.search.Brave
import org.libre.search.search.ImageResult
import org.libre.search.search.ResultFilter
import org.libre.search.search.SearchRepo
import org.libre.search.search.VideoResult
import org.libre.search.search.WebPage
import org.libre.search.search.WebResult

/** Everything shown for one search, kept alive while it stays in the back stack. */
class ResultsState(val query: String, initialTab: String) {
    var tab by mutableStateOf(initialTab)

    var all by mutableStateOf<AllResults?>(null)
    var allError by mutableStateOf<String?>(null)
    var morePages by mutableStateOf<List<WebResult>>(emptyList())
    var nextPage by mutableIntStateOf(1)
    var loadingMore by mutableStateOf(false)
    var noMore by mutableStateOf(false)

    var images by mutableStateOf<List<ImageResult>?>(null)
    var imagesError by mutableStateOf<String?>(null)

    var videos by mutableStateOf<List<VideoResult>?>(null)
    var videosError by mutableStateOf<String?>(null)

    var shopping by mutableStateOf<WebPage?>(null)
    var shoppingError by mutableStateOf<String?>(null)

    var forums by mutableStateOf<WebPage?>(null)
    var forumsError by mutableStateOf<String?>(null)

    var places by mutableStateOf<List<Business>?>(null)
    var placesHere by mutableStateOf<Place?>(null)
    var placesError by mutableStateOf<String?>(null)

    /** Shows an error in the current tab instead of letting a problem close the app. */
    fun failed(e: Throwable) {
        val msg = "Something went wrong: " + (e.message ?: e.javaClass.simpleName)
        when (tab) {
            "all" -> { allError = msg; if (all == null) all = AllResults(query, webError = msg) }
            "images" -> { imagesError = msg; if (images == null) images = emptyList() }
            "videos" -> { videosError = msg; if (videos == null) videos = emptyList() }
            "shopping" -> { shoppingError = msg; if (shopping == null) shopping = WebPage() }
            "forums" -> { forumsError = msg; if (forums == null) forums = WebPage() }
            "places" -> { placesError = msg; if (places == null) places = emptyList() }
        }
    }

    suspend fun load(ctx: Context) {
        when (tab) {
            "all" -> if (all == null) {
                val r = SearchRepo.all(ctx, query, 0)
                all = r
                allError = r.webError
                if (r.web?.moreAvailable == false) noMore = true
            }
            "images" -> if (images == null) try {
                images = ResultFilter.images(Brave.images(query))
            } catch (e: Exception) {
                imagesError = SearchRepo.errorText(e); images = emptyList()
            }
            "videos" -> if (videos == null) try {
                videos = ResultFilter.videos(Brave.videos(query))
            } catch (e: Exception) {
                videosError = SearchRepo.errorText(e); videos = emptyList()
            }
            "shopping" -> if (shopping == null) try {
                shopping = SearchRepo.shopping(query, 0)
            } catch (e: Exception) {
                shoppingError = SearchRepo.errorText(e); shopping = WebPage()
            }
            "forums" -> if (forums == null) try {
                forums = SearchRepo.forums(query, 0)
            } catch (e: Exception) {
                forumsError = SearchRepo.errorText(e); forums = WebPage()
            }
            "places" -> if (places == null) try {
                val here = all?.here ?: LocationHelper.current(ctx)
                placesHere = here
                places = Places.search(query, here)
            } catch (e: Exception) {
                placesError = SearchRepo.errorText(e); places = emptyList()
            }
        }
    }

    suspend fun loadMore(ctx: Context) {
        if (loadingMore || noMore || nextPage > 9) return
        loadingMore = true
        try {
            val r = SearchRepo.all(ctx, query, nextPage)
            val seen = (all?.web?.results.orEmpty() + morePages).map { it.url }.toHashSet()
            val fresh = r.web?.results.orEmpty().filter { it.url !in seen }
            morePages = morePages + fresh
            nextPage++
            if (fresh.isEmpty() || r.web?.moreAvailable == false) noMore = true
        } catch (_: Exception) {
            noMore = true
        } finally {
            loadingMore = false
        }
    }
}
