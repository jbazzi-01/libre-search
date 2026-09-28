package org.libre.search.news

import android.util.Xml
import org.libre.search.core.Http
import org.libre.search.core.stripHtml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Article(
    val sourceId: String,
    val title: String,
    val url: String,
    val summary: String,
    val image: String?,
    val time: Long,
    val author: String?,
)

object Rss {

    suspend fun fetch(url: String): String = Http.getString(
        url,
        mapOf("Accept" to "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5"),
        Http.BROWSER_UA
    )

    /** Looks for <link rel="alternate" type="application/rss+xml"> on a homepage. */
    suspend fun discover(site: String): List<String> = try {
        val html = Http.getString(site, emptyMap(), Http.BROWSER_UA)
        Regex("<link[^>]+>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
            .filter { it.contains("alternate", true) && (it.contains("rss", true) || it.contains("atom", true)) }
            .mapNotNull { tag -> Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) }
            .map { href -> resolve(site, href.replace("&amp;", "&")) }
            .distinct().toList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun resolve(base: String, href: String): String = try {
        java.net.URI(base).resolve(href).toString()
    } catch (_: Exception) {
        href
    }

    fun looksLikeFeed(body: String): Boolean {
        val head = body.take(2000).lowercase()
        return head.contains("<rss") || head.contains("<feed") || head.contains("<rdf:rdf")
    }

    fun parse(xml: String, sourceId: String, baseUrl: String): List<Article> {
        val out = ArrayList<Article>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml.trimStart('﻿', ' ', '\n', '\r', '\t')))
        var inItem = false
        var title = ""; var link = ""; var summary = ""; var content = ""; var date = ""; var image: String? = null; var author: String? = null
        var event = p.eventType
        try {
            while (event != XmlPullParser.END_DOCUMENT) {
                val name = p.name?.lowercase()
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (name == "item" || name == "entry") {
                            inItem = true
                            title = ""; link = ""; summary = ""; content = ""; date = ""; image = null; author = null
                        } else if (inItem) {
                            when (name) {
                                "title" -> title = readText(p)
                                "link" -> {
                                    val href = p.getAttributeValue(null, "href")
                                    val rel = p.getAttributeValue(null, "rel")
                                    if (href != null) {
                                        if (rel == null || rel == "alternate") link = href
                                        if (rel == "enclosure" && (p.getAttributeValue(null, "type") ?: "").startsWith("image")) image = href
                                    } else {
                                        val t = readText(p)
                                        if (t.isNotBlank()) link = t
                                    }
                                }
                                "guid", "id" -> {
                                    val t = readText(p)
                                    if (link.isBlank() && t.startsWith("http")) link = t
                                }
                                "description", "summary" -> summary = readText(p)
                                "content:encoded", "content" -> content = readText(p)
                                "pubdate", "published", "updated", "dc:date", "issued" -> {
                                    val t = readText(p)
                                    if (date.isBlank() || name == "pubdate" || name == "published") date = t
                                }
                                "dc:creator", "author" -> {
                                    val t = readText(p).trim()
                                    if (t.isNotBlank() && author == null) author = t
                                }
                                "name" -> if (author == null) { val t = readText(p).trim(); if (t.isNotBlank()) author = t }
                                "media:content", "media:thumbnail" -> {
                                    val u = p.getAttributeValue(null, "url")
                                    val medium = p.getAttributeValue(null, "medium") ?: p.getAttributeValue(null, "type") ?: "image"
                                    if (u != null && image == null && (medium.startsWith("image") || name == "media:thumbnail")) image = u
                                }
                                "enclosure" -> {
                                    val u = p.getAttributeValue(null, "url")
                                    val type = p.getAttributeValue(null, "type") ?: ""
                                    if (u != null && image == null && (type.startsWith("image") || u.matches(Regex(".*\\.(jpe?g|png|webp)(\\?.*)?$", RegexOption.IGNORE_CASE)))) image = u
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if ((name == "item" || name == "entry") && inItem) {
                            inItem = false
                            val html = content.ifBlank { summary }
                            val img = image ?: Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                            val text = summary.ifBlank { content }.stripHtml()
                            if (title.isNotBlank() && link.isNotBlank()) {
                                out += Article(
                                    sourceId = sourceId,
                                    title = title.stripHtml(),
                                    url = resolve(baseUrl, link.trim()),
                                    summary = if (text.length > 320) text.take(300).substringBeforeLast(' ') + "…" else text,
                                    image = img?.let { resolve(baseUrl, it.replace("&amp;", "&")) },
                                    time = parseDate(date),
                                    author = author?.stripHtml(),
                                )
                            }
                        }
                    }
                }
                event = p.next()
            }
        } catch (_: Exception) {
            // Return what was parsed before a malformed part of the feed.
        }
        return out
    }

    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(p.text)
                XmlPullParser.START_TAG -> {
                    depth++
                    // Atom content may contain XHTML markup: keep img sources for thumbnails.
                    if (p.name.equals("img", true)) p.getAttributeValue(null, "src")?.let { sb.append("<img src=\"$it\">") }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private val rfc822 = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z",
    )

    fun parseDate(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) {}
        try { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() } catch (_: Exception) {}
        for (f in rfc822) {
            try { return SimpleDateFormat(f, Locale.ENGLISH).parse(s)?.time ?: continue } catch (_: Exception) {}
        }
        try { return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(s)?.time ?: 0 } catch (_: Exception) {}
        try { return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(s)?.time ?: 0 } catch (_: Exception) {}
        return 0
    }
}
