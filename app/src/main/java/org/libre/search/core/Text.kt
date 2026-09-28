package org.libre.search.core

import java.net.URI
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object Text {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 1.0 - levenshtein(x, y).toDouble() / max(x.length, y.length)
    }

    fun host(url: String): String = try {
        (URI(url).host ?: "").removePrefix("www.").lowercase()
    } catch (_: Exception) {
        ""
    }

    fun hostMatches(host: String, domain: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        val d = domain.lowercase().removePrefix("www.")
        return h == d || h.endsWith(".$d")
    }

    fun breadcrumb(url: String): String = try {
        val u = URI(url)
        val parts = (u.path ?: "").split('/').filter { it.isNotBlank() }.take(3)
        val host = (u.host ?: "").removePrefix("www.")
        if (parts.isEmpty()) "https://$host" else "https://$host › " + parts.joinToString(" › ") { p ->
            if (p.length > 24) p.take(22) + "…" else p
        }
    } catch (_: Exception) {
        url
    }

    fun faviconUrl(url: String): String {
        val h = host(url)
        return "https://$h/favicon.ico"
    }

    fun ago(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val diff = (System.currentTimeMillis() - epochMillis) / 1000
        return when {
            diff < 0 -> ""
            diff < 3600 -> "${max(1, diff / 60)} min ago"
            diff < 86400 -> "${diff / 3600} h ago"
            diff < 86400 * 7 -> "${diff / 86400} d ago"
            else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
                .format(java.util.Date(epochMillis))
        }
    }
}
