package org.libre.search.panels

import org.json.JSONObject
import org.libre.search.core.Http
import org.libre.search.core.Prefs
import org.libre.search.core.Text
import org.libre.search.core.arr
import org.libre.search.core.obj
import org.libre.search.core.objects
import org.libre.search.core.str
import java.net.URLEncoder

data class WikiPanel(
    val title: String,
    val description: String?,
    val extract: String,
    val image: String?,
    val url: String,
    val wikidataId: String?,
    val facts: List<Pair<String, String>>,
    val website: String?,
    val socials: List<Pair<String, String>>,
)

/** Knowledge panel from Wikipedia + Wikidata. */
object Wiki {

    private fun lang() = if (Prefs.panelLang == "en") "en" else "fr"

    /** Wikipedia article for a query, only when its title clearly matches. */
    suspend fun lookup(query: String, strict: Boolean = true): WikiPanel? {
        val l = lang()
        val search = Http.getJson(
            Http.url("https://$l.wikipedia.org/w/rest.php/v1/search/title", mapOf("q" to query, "limit" to "3"))
        ).arr("pages").objects()
        val page = search.firstOrNull() ?: return null
        val title = page.str("title") ?: return null
        val cleanTitle = title.replace(Regex("\\s*\\(.*\\)$"), "")
        if (strict) {
            val sim = Text.similarity(cleanTitle, query)
            val qn = Text.normalize(query)
            val tn = Text.normalize(cleanTitle)
            if (sim < 0.85 && !(tn.length >= 4 && qn == tn)) return null
        }
        return byTitle(title, l)
    }

    suspend fun byTitle(title: String, l: String = lang()): WikiPanel? {
        val enc = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val s = Http.getJson("https://$l.wikipedia.org/api/rest_v1/page/summary/$enc")
        if (s.str("type") == "disambiguation") return null
        val qid = s.str("wikibase_item")
        val facts = if (qid != null) try { wikidataFacts(qid) } catch (_: Exception) { null } else null
        return WikiPanel(
            title = s.str("title") ?: title,
            description = s.str("description"),
            extract = s.str("extract") ?: "",
            image = s.obj("thumbnail").str("source") ?: s.obj("originalimage").str("source"),
            url = s.obj("content_urls").obj("mobile").str("page") ?: "https://$l.wikipedia.org/wiki/$enc",
            wikidataId = qid,
            facts = facts?.facts ?: emptyList(),
            website = facts?.website,
            socials = facts?.socials ?: emptyList(),
        )
    }

    /** Link to the Wikipedia article for a Wikidata item, preferring the panel language. */
    suspend fun articleForWikidata(qid: String): String? = try {
        val j = Http.getJson(
            Http.url(
                "https://www.wikidata.org/w/api.php",
                mapOf("action" to "wbgetentities", "ids" to qid, "props" to "sitelinks/urls", "sitefilter" to "frwiki|enwiki", "format" to "json")
            )
        )
        val links = j.obj("entities").obj(qid).obj("sitelinks")
        val pref = if (lang() == "fr") listOf("frwiki", "enwiki") else listOf("enwiki", "frwiki")
        pref.firstNotNullOfOrNull { links.obj(it).str("url") }
    } catch (_: Exception) {
        null
    }

    suspend fun articleForTitle(title: String): String? = try {
        lookup(title, strict = false)?.url
    } catch (_: Exception) {
        null
    }

    private class Facts(val facts: List<Pair<String, String>>, val website: String?, val socials: List<Pair<String, String>>)

    private val props = listOf(
        "P569" to ("Born" to "Naissance"),
        "P19" to ("Place of birth" to "Lieu de naissance"),
        "P570" to ("Died" to "Décès"),
        "P20" to ("Place of death" to "Lieu de décès"),
        "P27" to ("Nationality" to "Nationalité"),
        "P106" to ("Occupation" to "Profession"),
        "P102" to ("Political party" to "Parti politique"),
        "P26" to ("Spouse" to "Conjoint"),
        "P69" to ("Education" to "Formation"),
        "P571" to ("Founded" to "Fondation"),
        "P112" to ("Founders" to "Fondateurs"),
        "P159" to ("Headquarters" to "Siège"),
        "P169" to ("CEO" to "Direction"),
        "P452" to ("Industry" to "Secteur"),
        "P17" to ("Country" to "Pays"),
        "P36" to ("Capital" to "Capitale"),
        "P1082" to ("Population" to "Population"),
        "P2046" to ("Area" to "Superficie"),
        "P6" to ("Head of government" to "Chef du gouvernement"),
        "P35" to ("Head of state" to "Chef d'État"),
        "P131" to ("Located in" to "Localisation"),
        "P136" to ("Genre" to "Genre"),
        "P264" to ("Record label" to "Label"),
        "P50" to ("Author" to "Auteur"),
        "P577" to ("Published" to "Publication"),
        "P178" to ("Developer" to "Développeur"),
    )

    private suspend fun wikidataFacts(qid: String): Facts {
        val l = lang()
        val j = Http.getJson(
            Http.url(
                "https://www.wikidata.org/w/api.php",
                mapOf("action" to "wbgetentities", "ids" to qid, "props" to "claims", "format" to "json")
            )
        )
        val claims = j.obj("entities").obj(qid).obj("claims") ?: return Facts(emptyList(), null, emptyList())

        fun values(p: String): List<JSONObject> = claims.arr(p).objects()
            .filter { it.str("rank") != "deprecated" }
            .mapNotNull { it.obj("mainsnak").obj("datavalue") }

        val needLabels = LinkedHashSet<String>()
        props.forEach { (p, _) ->
            values(p).take(4).forEach { dv ->
                dv.obj("value").str("id")?.let { needLabels += it }
                dv.obj("value").str("unit")?.substringAfterLast('/')?.takeIf { it.startsWith("Q") }?.let { needLabels += it }
            }
        }
        val labels = HashMap<String, String>()
        needLabels.chunked(45).forEach { chunk ->
            try {
                val lj = Http.getJson(
                    Http.url(
                        "https://www.wikidata.org/w/api.php",
                        mapOf("action" to "wbgetentities", "ids" to chunk.joinToString("|"), "props" to "labels", "languages" to "$l|en", "format" to "json")
                    )
                )
                chunk.forEach { id ->
                    val lab = lj.obj("entities").obj(id).obj("labels")
                    (lab.obj(l).str("value") ?: lab.obj("en").str("value"))?.let { labels[id] = it }
                }
            } catch (_: Exception) {
            }
        }

        fun render(dv: JSONObject): String? {
            val type = dv.str("type")
            val v = dv.opt("value")
            return when (type) {
                "wikibase-entityid" -> labels[(v as? JSONObject).str("id") ?: ""]
                "time" -> {
                    val t = (v as? JSONObject).str("time") ?: return null
                    val prec = (v as? JSONObject)?.optInt("precision") ?: 11
                    val m = Regex("([+-])(\\d+)-(\\d\\d)-(\\d\\d)").find(t) ?: return null
                    val (sign, y, mo, d) = m.destructured
                    val year = y.trimStart('0') + if (sign == "-") " BC" else ""
                    when {
                        prec >= 11 -> formatDate(year, mo.toInt(), d.toInt(), l)
                        prec == 10 -> "${monthName(mo.toInt(), l)} $year"
                        else -> year
                    }
                }
                "quantity" -> {
                    val o = v as? JSONObject ?: return null
                    val amount = o.str("amount")?.removePrefix("+")?.toDoubleOrNull() ?: return null
                    val unitId = o.str("unit")?.substringAfterLast('/')
                    val unit = when (unitId) { "Q712226" -> " km²"; "1", null -> ""; else -> labels[unitId]?.let { " $it" } ?: "" }
                    String.format(java.util.Locale.FRANCE, "%,.0f", amount) + unit
                }
                "string" -> v as? String
                "monolingualtext" -> (v as? JSONObject).str("text")
                else -> null
            }
        }

        val facts = ArrayList<Pair<String, String>>()
        props.forEach { (p, names) ->
            val rendered = values(p).mapNotNull { render(it) }.distinct()
            if (rendered.isNotEmpty()) {
                val name = if (l == "fr") names.second else names.first
                val maxItems = if (p == "P106" || p == "P112" || p == "P136") 4 else 2
                val value = if (p == "P1082") rendered.last() else rendered.take(maxItems).joinToString(", ")
                facts += name to value
            }
        }
        val website = values("P856").firstNotNullOfOrNull { it.opt("value") as? String }
        val socials = ArrayList<Pair<String, String>>()
        values("P2002").firstNotNullOfOrNull { it.opt("value") as? String }?.let { socials += "X" to "https://x.com/$it" }
        values("P2003").firstNotNullOfOrNull { it.opt("value") as? String }?.let { socials += "Instagram" to "https://www.instagram.com/$it" }
        values("P4033").firstNotNullOfOrNull { it.opt("value") as? String }?.let { h ->
            val parts = h.split('@').filter { it.isNotBlank() }
            if (parts.size == 2) socials += "Mastodon" to "https://${parts[1]}/@${parts[0]}"
        }
        values("P12361").firstNotNullOfOrNull { it.opt("value") as? String }?.let { socials += "Bluesky" to "https://bsky.app/profile/$it" }
        values("P2397").firstNotNullOfOrNull { it.opt("value") as? String }?.let { socials += "YouTube" to "https://www.youtube.com/channel/$it" }
        return Facts(facts, website, socials)
    }

    private fun monthName(m: Int, l: String): String {
        val fr = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
        val en = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val list = if (l == "fr") fr else en
        return list.getOrElse(m - 1) { "" }
    }

    private fun formatDate(year: String, m: Int, d: Int, l: String): String =
        if (l == "fr") "$d ${monthName(m, l)} $year" else "${monthName(m, l)} $d, $year"
}
