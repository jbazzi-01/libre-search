package org.libre.search.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.libre.search.browser.Tabs
import org.libre.search.core.Prefs
import org.libre.search.data.LibraryStore
import org.libre.search.player.PlayerActivity
import org.libre.search.player.VideoLinks
import org.libre.search.ui.search.ResultsState

sealed class Screen {
    data object Home : Screen()
    data class SearchInput(val initial: String, val tab: String = "all", val inTab: Boolean = false) : Screen()
    class Results(val state: ResultsState) : Screen()
    data object Browser : Screen()
    data object TabSwitcher : Screen()
    data object News : Screen()
    data object Library : Screen()
    data object Settings : Screen()
}

object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current get() = stack.last()
    val toast = mutableStateOf<String?>(null)

    fun push(s: Screen) {
        stack.add(s)
    }

    fun replaceTop(s: Screen) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(s)
    }

    /** Bottom-bar destinations reset the stack to a single root. */
    fun root(s: Screen) {
        stack.clear()
        stack.add(Screen.Home)
        if (s != Screen.Home) stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun search(query: String, tab: String = "all", inTab: Boolean = false) {
        val q = query.trim()
        if (q.isEmpty()) return
        val direct = directUrl(q)
        if (direct != null) {
            if (current is Screen.SearchInput) pop()
            val cur = Tabs.current.value
            if (inTab && cur != null && current == Screen.Browser) cur.webView?.loadUrl(direct) else openUrl(null, direct)
            return
        }
        LibraryStore.addSearch(q)
        val screen = Screen.Results(ResultsState(q, tab))
        if (current is Screen.SearchInput) replaceTop(screen) else push(screen)
    }

    /** "example.com" or a full URL typed in the search box opens the site directly. */
    private fun directUrl(q: String): String? {
        if (q.contains(' ')) return null
        if (q.startsWith("http://") || q.startsWith("https://")) return q
        return if (Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE).matches(q)) "https://$q" else null
    }

    /** Opens a link: the video player for supported videos, else the built-in browser (or an outside one). */
    fun openUrl(ctx: Context?, url: String, forceBrowser: Boolean = false, private: Boolean = false) {
        if (!forceBrowser && Prefs.videoPlayer && VideoLinks.isStream(url) && ctx != null) {
            PlayerActivity.start(ctx, url)
            return
        }
        if (Prefs.builtInBrowser || ctx == null) {
            val cur = Tabs.current.value
            val blank = cur != null && (cur.url.value.isBlank() || cur.url.value == "about:blank")
            val tab = if (blank && cur != null && (!private || cur.isPrivate)) {
                cur.url.value = url
                cur.webView?.loadUrl(url)
                cur
            } else Tabs.open(url, private)
            if (forceBrowser) tab.allowVideoPage = url
            if (current != Screen.Browser) push(Screen.Browser)
        } else {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                Tabs.open(url)
                push(Screen.Browser)
            }
        }
    }
}
