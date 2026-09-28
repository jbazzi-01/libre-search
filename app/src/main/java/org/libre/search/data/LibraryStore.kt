package org.libre.search.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import org.libre.search.core.Prefs
import java.io.File

data class HistoryEntry(val kind: String, val title: String, val url: String, val time: Long)
data class Bookmark(val title: String, val url: String, val folder: String, val time: Long)

/** History and bookmarks, stored only in the app's private storage on the phone. */
object LibraryStore {
    private lateinit var dir: File
    val history = mutableStateListOf<HistoryEntry>()
    val bookmarks = mutableStateListOf<Bookmark>()

    fun init(context: Context) {
        dir = context.filesDir
        load()
    }

    private fun load() {
        try {
            val f = File(dir, "history.json")
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    history += HistoryEntry(o.optString("k"), o.optString("t"), o.optString("u"), o.optLong("w"))
                }
            }
        } catch (_: Exception) {
        }
        try {
            val f = File(dir, "bookmarks.json")
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    bookmarks += Bookmark(o.optString("t"), o.optString("u"), o.optString("f", ""), o.optLong("w"))
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun saveHistory() {
        val a = JSONArray()
        history.take(3000).forEach {
            a.put(JSONObject().put("k", it.kind).put("t", it.title).put("u", it.url).put("w", it.time))
        }
        try { File(dir, "history.json").writeText(a.toString()) } catch (_: Exception) {}
    }

    private fun saveBookmarks() {
        val a = JSONArray()
        bookmarks.forEach {
            a.put(JSONObject().put("t", it.title).put("u", it.url).put("f", it.folder).put("w", it.time))
        }
        try { File(dir, "bookmarks.json").writeText(a.toString()) } catch (_: Exception) {}
    }

    fun addSearch(query: String) {
        if (!Prefs.saveHistory || query.isBlank()) return
        history.removeAll { it.kind == "search" && it.title.equals(query, true) }
        history.add(0, HistoryEntry("search", query.trim(), "", System.currentTimeMillis()))
        saveHistory()
    }

    fun addPage(title: String, url: String) {
        if (!Prefs.saveHistory || url.isBlank() || url.startsWith("about:")) return
        val first = history.firstOrNull { it.kind == "page" }
        if (first != null && first.url == url) {
            if (title.isNotBlank() && first.title != title) {
                history[history.indexOf(first)] = first.copy(title = title)
                saveHistory()
            }
            return
        }
        history.add(0, HistoryEntry("page", title.ifBlank { url }, url, System.currentTimeMillis()))
        saveHistory()
    }

    fun searchSuggestions(prefix: String, limit: Int = 4): List<String> {
        val p = prefix.trim().lowercase()
        return history.asSequence().filter { it.kind == "search" }
            .map { it.title }
            .filter { p.isEmpty() || it.lowercase().startsWith(p) }
            .distinct().take(limit).toList()
    }

    fun removeHistory(entry: HistoryEntry) { history.remove(entry); saveHistory() }
    fun clearHistory() { history.clear(); saveHistory() }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(title: String, url: String, folder: String = "") {
        val existing = bookmarks.firstOrNull { it.url == url }
        if (existing != null) bookmarks.remove(existing)
        else bookmarks.add(0, Bookmark(title.ifBlank { url }, url, folder, System.currentTimeMillis()))
        saveBookmarks()
    }

    fun updateBookmark(old: Bookmark, new: Bookmark) {
        val i = bookmarks.indexOf(old)
        if (i >= 0) { bookmarks[i] = new; saveBookmarks() }
    }

    fun removeBookmark(b: Bookmark) { bookmarks.remove(b); saveBookmarks() }
}
