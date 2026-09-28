package org.libre.search.panels

import org.json.JSONObject
import org.libre.search.core.Http
import org.libre.search.core.Prefs
import org.libre.search.core.Text
import org.libre.search.core.arr
import org.libre.search.core.dbl
import org.libre.search.core.int
import org.libre.search.core.obj
import org.libre.search.core.objects
import org.libre.search.core.str
import org.libre.search.search.QueryIntent

data class CastMember(val name: String, val role: String, val photo: String?, val personId: Int)

data class ScreenWork(
    val id: Int,
    val isTv: Boolean,
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val genres: List<String>,
    val runtime: String?,
    val rating: Double?,
    val voteCount: Int,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val directors: List<String>,
    val writers: List<String>,
    val cast: List<CastMember>,
    val releaseDate: String?,
    val certification: String?,
    val budget: Long?,
    val boxOffice: Long?,
    val seasons: Int?,
    val networks: List<String>,
    val watchProviders: List<String>,
    val trailerUrl: String?,
    val wikidataId: String?,
    val imdbId: String?,
    val tagline: String?,
)

data class KnownFor(val title: String, val year: String?, val poster: String?, val id: Int, val isTv: Boolean)

data class PersonInfo(
    val id: Int,
    val name: String,
    val photo: String?,
    val department: String?,
    val bio: String,
    val birthday: String?,
    val deathday: String?,
    val birthplace: String?,
    val knownFor: List<KnownFor>,
    val wikidataId: String?,
    val imdbId: String?,
    val instagram: String?,
)

sealed class TmdbHit {
    data class Work(val work: ScreenWork) : TmdbHit()
    data class Person(val person: PersonInfo) : TmdbHit()
}

/** Movies, TV shows and people from TMDB (free API key required). */
object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    const val IMG = "https://image.tmdb.org/t/p/"

    private fun lang() = if (Prefs.panelLang == "en") "en-US" else "fr-FR"

    private suspend fun get(path: String, params: Map<String, String?> = emptyMap()): JSONObject {
        val key = Prefs.tmdbKey
        val bearer = key.length > 60
        val all = params + ("language" to lang()) + (if (bearer) emptyMap() else mapOf("api_key" to key))
        val headers = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return Http.getJson(Http.url("$BASE$path", all), headers)
    }

    fun img(path: String?, size: String = "w342"): String? = path?.let { "$IMG$size$it" }

    /** Finds a movie, show or person that clearly matches the query, if any. */
    suspend fun lookup(query: String): TmdbHit? {
        if (Prefs.tmdbKey.isBlank()) return null
        val screenHint = QueryIntent.mentionsScreen(query)
        val q = QueryIntent.stripScreenWords(query).replace(Regex("\\b(19|20)\\d{2}\\b"), "").trim()
        if (q.isBlank()) return null
        val year = Regex("\\b(19|20)\\d{2}\\b").find(query)?.value
        val res = get("/search/multi", mapOf("query" to q, "include_adult" to "false")).arr("results").objects()
        if (res.isEmpty()) return null

        fun titleOf(o: JSONObject) = o.str("title") ?: o.str("name") ?: ""
        fun origOf(o: JSONObject) = o.str("original_title") ?: o.str("original_name") ?: ""
        fun score(o: JSONObject): Double {
            val sim = maxOf(Text.similarity(titleOf(o), q), Text.similarity(origOf(o), q))
            val pop = (o.dbl("popularity") ?: 0.0)
            val y = (o.str("release_date") ?: o.str("first_air_date") ?: "").take(4)
            var s = sim * 10 + kotlin.math.ln(1 + pop)
            if (year != null && y == year) s += 4
            return s
        }

        val best = res.take(8).maxByOrNull { score(it) } ?: return null
        val sim = maxOf(Text.similarity(titleOf(best), q), Text.similarity(origOf(best), q))
        val votes = best.int("vote_count") ?: 0
        val pop = best.dbl("popularity") ?: 0.0
        val type = best.str("media_type") ?: return null
        val strongEnough = when {
            screenHint -> sim >= 0.6
            type == "person" -> sim >= 0.9 && pop >= 3
            else -> sim >= 0.92 && (votes >= 150 || pop >= 15)
        }
        if (!strongEnough) return null
        val id = best.optInt("id")
        return when (type) {
            "movie" -> TmdbHit.Work(movie(id))
            "tv" -> TmdbHit.Work(tv(id))
            "person" -> TmdbHit.Person(person(id))
            else -> null
        }
    }

    private fun providers(j: JSONObject): List<String> {
        val region = j.obj("watch/providers").obj("results").obj(Prefs.country)
        val names = LinkedHashSet<String>()
        listOf("flatrate", "free", "ads", "rent", "buy").forEach { k ->
            region.arr(k).objects().forEach { p -> p.str("provider_name")?.let { names += it } }
        }
        return names.take(6)
    }

    private fun trailer(j: JSONObject): String? {
        val vids = j.obj("videos").arr("results").objects()
        val t = vids.firstOrNull { it.str("site") == "YouTube" && it.str("type") == "Trailer" }
            ?: vids.firstOrNull { it.str("site") == "YouTube" }
        return t?.str("key")?.let { "https://www.youtube.com/watch?v=$it" }
    }

    suspend fun movie(id: Int): ScreenWork {
        val j = get(
            "/movie/$id",
            mapOf(
                "append_to_response" to "credits,external_ids,release_dates,watch/providers,videos",
                "include_video_language" to "fr,en,null"
            )
        )
        val crew = j.obj("credits").arr("crew").objects()
        val cert = j.obj("release_dates").arr("results").objects()
            .firstOrNull { it.str("iso_3166_1") == Prefs.country }
            ?.arr("release_dates").objects().mapNotNull { it.str("certification") }.firstOrNull()
        val runtime = j.int("runtime")?.takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}min" }
        return ScreenWork(
            id = id, isTv = false,
            title = j.str("title") ?: "",
            originalTitle = j.str("original_title"),
            year = j.str("release_date")?.take(4),
            genres = j.arr("genres").objects().mapNotNull { it.str("name") },
            runtime = runtime,
            rating = j.dbl("vote_average")?.takeIf { it > 0 },
            voteCount = j.int("vote_count") ?: 0,
            overview = j.str("overview") ?: "",
            poster = img(j.str("poster_path")),
            backdrop = img(j.str("backdrop_path"), "w780"),
            directors = crew.filter { it.str("job") == "Director" }.mapNotNull { it.str("name") }.distinct(),
            writers = crew.filter { it.str("department") == "Writing" }.mapNotNull { it.str("name") }.distinct().take(4),
            cast = j.obj("credits").arr("cast").objects().take(20).map {
                CastMember(it.str("name") ?: "", it.str("character") ?: "", img(it.str("profile_path"), "w185"), it.optInt("id"))
            },
            releaseDate = j.str("release_date"),
            certification = cert,
            budget = j.optLong("budget").takeIf { it > 0 },
            boxOffice = j.optLong("revenue").takeIf { it > 0 },
            seasons = null,
            networks = j.arr("production_companies").objects().mapNotNull { it.str("name") }.take(3),
            watchProviders = providers(j),
            trailerUrl = trailer(j),
            wikidataId = j.obj("external_ids").str("wikidata_id"),
            imdbId = j.str("imdb_id"),
            tagline = j.str("tagline"),
        )
    }

    suspend fun tv(id: Int): ScreenWork {
        val j = get(
            "/tv/$id",
            mapOf(
                "append_to_response" to "aggregate_credits,external_ids,content_ratings,watch/providers,videos",
                "include_video_language" to "fr,en,null"
            )
        )
        val creators = j.arr("created_by").objects().mapNotNull { it.str("name") }
        val cert = j.obj("content_ratings").arr("results").objects()
            .firstOrNull { it.str("iso_3166_1") == Prefs.country }?.str("rating")
        return ScreenWork(
            id = id, isTv = true,
            title = j.str("name") ?: "",
            originalTitle = j.str("original_name"),
            year = j.str("first_air_date")?.take(4)?.let { start ->
                val end = j.str("last_air_date")?.take(4)
                val ongoing = j.optBoolean("in_production")
                if (ongoing) "$start–" else if (end != null && end != start) "$start–$end" else start
            },
            genres = j.arr("genres").objects().mapNotNull { it.str("name") },
            runtime = j.arr("episode_run_time").let { a -> if (a != null && a.length() > 0) "${a.optInt(0)} min" else null },
            rating = j.dbl("vote_average")?.takeIf { it > 0 },
            voteCount = j.int("vote_count") ?: 0,
            overview = j.str("overview") ?: "",
            poster = img(j.str("poster_path")),
            backdrop = img(j.str("backdrop_path"), "w780"),
            directors = creators,
            writers = emptyList(),
            cast = j.obj("aggregate_credits").arr("cast").objects().take(20).map {
                val role = it.arr("roles").objects().firstOrNull()?.str("character") ?: ""
                CastMember(it.str("name") ?: "", role, img(it.str("profile_path"), "w185"), it.optInt("id"))
            },
            releaseDate = j.str("first_air_date"),
            certification = cert,
            budget = null, boxOffice = null,
            seasons = j.int("number_of_seasons"),
            networks = j.arr("networks").objects().mapNotNull { it.str("name") }.take(3),
            watchProviders = providers(j),
            trailerUrl = trailer(j),
            wikidataId = j.obj("external_ids").str("wikidata_id"),
            imdbId = j.obj("external_ids").str("imdb_id"),
            tagline = j.str("tagline"),
        )
    }

    suspend fun person(id: Int): PersonInfo {
        val j = get("/person/$id", mapOf("append_to_response" to "combined_credits,external_ids"))
        var bio = j.str("biography") ?: ""
        if (bio.isBlank() && Prefs.panelLang != "en") {
            bio = try {
                val key = Prefs.tmdbKey
                val bearer = key.length > 60
                val u = Http.url("$BASE/person/$id", mapOf("language" to "en-US") + (if (bearer) emptyMap() else mapOf("api_key" to key)))
                Http.getJson(u, if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()).str("biography") ?: ""
            } catch (_: Exception) { "" }
        }
        val dept = j.str("known_for_department")
        val credits = if (dept == "Acting") j.obj("combined_credits").arr("cast").objects()
        else j.obj("combined_credits").arr("crew").objects()
        val known = credits
            .filter { it.str("poster_path") != null }
            .filterNot { c -> (c.arr("genre_ids")?.let { g -> (0 until g.length()).any { g.optInt(it) in setOf(10767, 10763) } } ?: false) }
            .sortedByDescending { (it.dbl("vote_count") ?: 0.0) }
            .distinctBy { it.optInt("id") }
            .take(12)
            .map {
                KnownFor(
                    it.str("title") ?: it.str("name") ?: "",
                    (it.str("release_date") ?: it.str("first_air_date"))?.take(4),
                    img(it.str("poster_path"), "w185"),
                    it.optInt("id"),
                    it.str("media_type") == "tv",
                )
            }
        val ext = j.obj("external_ids")
        return PersonInfo(
            id = id,
            name = j.str("name") ?: "",
            photo = img(j.str("profile_path"), "w342"),
            department = dept,
            bio = bio,
            birthday = j.str("birthday"),
            deathday = j.str("deathday"),
            birthplace = j.str("place_of_birth"),
            knownFor = known,
            wikidataId = ext.str("wikidata_id"),
            imdbId = j.str("imdb_id") ?: ext.str("imdb_id"),
            instagram = ext.str("instagram_id"),
        )
    }
}
