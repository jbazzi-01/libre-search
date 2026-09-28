package org.libre.search.news

import org.json.JSONArray
import org.json.JSONObject
import org.libre.search.core.Prefs

enum class Tendency(val label: String) {
    COMMUNIST("Communist"),
    ANARCHIST("Anarchist"),
    LEFT("Socialist & independent left"),
    PALESTINE("Palestine"),
    GOOD("Good news"),
    CUSTOM("Added by you"),
}

data class Source(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val tendency: Tendency,
    val feeds: List<String>,
    val label: String? = null,
    val custom: Boolean = false,
)

object Sources {

    val builtIn = listOf(
        Source("humanite", "L'Humanité", "https://www.humanite.fr", "fr", Tendency.COMMUNIST,
            listOf("https://www.humanite.fr/feed", "https://www.humanite.fr/rss/actu.rss")),
        Source("revperm", "Révolution Permanente", "https://www.revolutionpermanente.fr", "fr", Tendency.COMMUNIST,
            listOf("https://www.revolutionpermanente.fr/spip.php?page=backend", "https://www.revolutionpermanente.fr/rss")),

        Source("lundimatin", "Lundi matin", "https://lundi.am", "fr", Tendency.ANARCHIST,
            listOf("https://lundi.am/spip.php?page=backend", "https://lundi.am/rss")),
        Source("parisluttes", "Paris-luttes.info", "https://paris-luttes.info", "fr", Tendency.ANARCHIST,
            listOf("https://paris-luttes.info/spip.php?page=backend", "https://paris-luttes.info/home/chroot_ml/ml-paris/ml-paris/public_html/spip.php?page=backend")),
        Source("crimethinc", "CrimethInc", "https://crimethinc.com", "en", Tendency.ANARCHIST,
            listOf("https://crimethinc.com/feed", "https://crimethinc.com/rss.xml")),
        Source("igd", "It's Going Down", "https://itsgoingdown.org", "en", Tendency.ANARCHIST,
            listOf("https://itsgoingdown.org/feed/")),
        Source("freedom", "Freedom News", "https://freedomnews.org.uk", "en", Tendency.ANARCHIST,
            listOf("https://freedomnews.org.uk/feed/")),

        Source("jacobin", "Jacobin", "https://jacobin.com", "en", Tendency.LEFT,
            listOf("https://jacobin.com/feed", "https://jacobin.com/feed/")),
        Source("frustration", "Frustration", "https://www.frustrationmagazine.fr", "fr", Tendency.LEFT,
            listOf("https://www.frustrationmagazine.fr/feed/")),
        Source("rapportsdeforce", "Rapports de Force", "https://rapportsdeforce.fr", "fr", Tendency.LEFT,
            listOf("https://rapportsdeforce.fr/feed", "https://rapportsdeforce.fr/feed/")),
        Source("reporterre", "Reporterre", "https://reporterre.net", "fr", Tendency.LEFT,
            listOf("https://reporterre.net/spip.php?page=backend", "https://reporterre.net/spip.php?page=backend-simple")),
        Source("novara", "Novara Media", "https://novaramedia.com", "en", Tendency.LEFT,
            listOf("https://novaramedia.com/feed/")),
        Source("truthout", "Truthout", "https://truthout.org", "en", Tendency.LEFT,
            listOf("https://truthout.org/feed/", "https://truthout.org/latest/feed/")),
        Source("democracynow", "Democracy Now!", "https://www.democracynow.org", "en", Tendency.LEFT,
            listOf("https://www.democracynow.org/democracynow.rss")),
        Source("mondediplo", "Le Monde diplomatique", "https://www.monde-diplomatique.fr", "fr", Tendency.LEFT,
            listOf("https://www.monde-diplomatique.fr/rss/", "https://www.monde-diplomatique.fr/recents.xml"),
            label = "Big business owned"),

        Source("mondoweiss", "Mondoweiss", "https://mondoweiss.net", "en", Tendency.PALESTINE,
            listOf("https://mondoweiss.net/feed/")),
        Source("ei", "The Electronic Intifada", "https://electronicintifada.net", "en", Tendency.PALESTINE,
            listOf("https://electronicintifada.net/rss.xml", "https://electronicintifada.net/news.xml")),

        Source("positivenews", "Positive News", "https://www.positive.news", "en", Tendency.GOOD,
            listOf("https://www.positive.news/feed/")),
        Source("cheerful", "Reasons to be Cheerful", "https://reasonstobecheerful.world", "en", Tendency.GOOD,
            listOf("https://reasonstobecheerful.world/feed/")),
    )

    private val stateFunded = listOf(
        "rt.com", "sputniknews.com", "sputnikglobe.com", "cgtn.com", "xinhuanet.com", "news.cn", "globaltimes.cn",
        "chinadaily.com.cn", "people.cn", "telesurtv.net", "telesurenglish.net", "presstv.ir", "tass.com", "ria.ru",
        "france24.com", "rfi.fr", "francetvinfo.fr", "radiofrance.fr", "franceinter.fr", "arte.tv", "bbc.co.uk", "bbc.com",
        "dw.com", "voanews.com", "rferl.org", "aljazeera.com", "aljazeera.net", "trtworld.com", "aa.com.tr", "nhk.or.jp",
        "cbc.ca", "abc.net.au", "npr.org", "pbs.org", "kan.org.il",
    )

    private val bigBusinessOrRight = listOf(
        "lemonde.fr", "lefigaro.fr", "liberation.fr", "bfmtv.com", "cnews.fr", "europe1.fr", "lejdd.fr", "parismatch.com",
        "leparisien.fr", "lesechos.fr", "lexpress.fr", "lepoint.fr", "nouvelobs.com", "20minutes.fr", "tf1info.fr", "lci.fr",
        "rtl.fr", "latribune.fr", "challenges.fr", "valeursactuelles.com", "causeur.fr", "bvoltaire.fr", "fdesouche.com",
        "cnn.com", "foxnews.com", "nytimes.com", "washingtonpost.com", "bloomberg.com", "reuters.com", "wsj.com", "ft.com",
        "economist.com", "telegraph.co.uk", "dailymail.co.uk", "thesun.co.uk", "thetimes.co.uk", "nbcnews.com", "cbsnews.com",
        "abcnews.go.com", "msnbc.com", "usatoday.com", "forbes.com", "businessinsider.com", "huffpost.com", "vox.com",
        "axios.com", "politico.com", "politico.eu", "nypost.com", "breitbart.com", "dailywire.com", "thegatewaypundit.com",
        "newsweek.com", "time.com", "yahoo.com", "msn.com", "news.google.com",
    )

    /** Pre-screens a new source against the list rules. Returns the reason it can't be added, or null. */
    fun rejection(url: String): String? {
        val host = org.libre.search.core.Text.host(if (url.startsWith("http")) url else "https://$url")
        fun hit(list: List<String>) = list.any { org.libre.search.core.Text.hostMatches(host, it.substringBefore('/')) }
        return when {
            hit(org.libre.search.search.ResultFilter.israeliOutlets) -> "This outlet is Israeli or pro-Israel."
            hit(stateFunded) -> "This outlet is state-funded."
            hit(bigBusinessOrRight) -> "This outlet is corporate-owned or right-wing."
            else -> null
        }
    }

    fun custom(): List<Source> = try {
        val a = JSONArray(Prefs.customFeedsJson)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Source(
                id = o.getString("id"),
                name = o.getString("name"),
                site = o.optString("site"),
                lang = o.optString("lang", "fr"),
                tendency = Tendency.CUSTOM,
                feeds = listOf(o.getString("feed")),
                custom = true,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun all(): List<Source> = builtIn + custom()
    fun enabled(): List<Source> = all().filter { it.id !in Prefs.disabledFeeds }

    fun addCustom(name: String, site: String, feed: String, lang: String) {
        val a = try { JSONArray(Prefs.customFeedsJson) } catch (_: Exception) { JSONArray() }
        a.put(
            JSONObject().put("id", "custom_" + System.currentTimeMillis())
                .put("name", name).put("site", site).put("feed", feed).put("lang", lang)
        )
        Prefs.customFeedsJson = a.toString()
    }

    fun removeCustom(id: String) {
        val a = try { JSONArray(Prefs.customFeedsJson) } catch (_: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") != id) out.put(o)
        }
        Prefs.customFeedsJson = out.toString()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        Prefs.disabledFeeds = if (enabled) Prefs.disabledFeeds - id else Prefs.disabledFeeds + id
    }
}
