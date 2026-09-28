package org.libre.search.panels

import org.json.JSONObject
import org.libre.search.core.Http
import org.libre.search.core.Place
import org.libre.search.core.arr
import org.libre.search.core.obj
import org.libre.search.core.objects
import org.libre.search.core.str
import org.libre.search.search.QueryIntent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Business(
    val name: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val phone: String?,
    val website: String?,
    val openingHours: String?,
    val openNow: Boolean?,
    val hoursToday: String?,
    val distanceM: Int?,
    val cuisine: String?,
    val wheelchair: String?,
    val osmUrl: String,
)

/** Local businesses from OpenStreetMap (Overpass for categories, Nominatim for names). */
object Places {

    suspend fun search(query: String, here: Place?): List<Business> {
        val plan = QueryIntent.local(query)
        if (plan?.tag != null && here != null) {
            val found = overpass(plan.tag, here, 2500)
            if (found.size >= 3) return found
            return (found + overpass(plan.tag, here, 8000)).distinctBy { it.osmUrl }
        }
        val name = plan?.nameQuery ?: query
        return nominatim(name, here)
    }

    private suspend fun overpass(filter: String, here: Place, radius: Int): List<Business> {
        val q = """
            [out:json][timeout:20];
            (
              node$filter["name"](around:$radius,${here.lat},${here.lon});
              way$filter["name"](around:$radius,${here.lat},${here.lon});
            );
            out center tags 40;
        """.trimIndent()
        val body = Http.postForm("https://overpass-api.de/api/interpreter", mapOf("data" to q))
        val els = JSONObject(body).arr("elements").objects()
        return els.mapNotNull { e ->
            val tags = e.obj("tags") ?: return@mapNotNull null
            val lat = e.optDouble("lat").takeIf { !it.isNaN() } ?: e.obj("center")?.optDouble("lat") ?: return@mapNotNull null
            val lon = e.optDouble("lon").takeIf { !it.isNaN() } ?: e.obj("center")?.optDouble("lon") ?: return@mapNotNull null
            toBusiness(tags, lat, lon, "${e.str("type")}/${e.optLong("id")}", here, null)
        }.sortedBy { it.distanceM ?: Int.MAX_VALUE }.take(30)
    }

    private suspend fun nominatim(name: String, here: Place?): List<Business> {
        val params = mutableMapOf<String, String?>(
            "q" to name, "format" to "jsonv2", "limit" to "10",
            "extratags" to "1", "addressdetails" to "1",
        )
        if (here != null) {
            val d = 0.25
            params["viewbox"] = "${here.lon - d},${here.lat + d},${here.lon + d},${here.lat - d}"
            params["bounded"] = "1"
        }
        val arr = org.json.JSONArray(Http.getString(Http.url("https://nominatim.openstreetmap.org/search", params)))
        return arr.objects().mapNotNull { o ->
            val cls = o.str("category") ?: o.str("class") ?: ""
            if (cls !in setOf("amenity", "shop", "tourism", "leisure", "office", "craft", "healthcare", "club")) return@mapNotNull null
            val tags = JSONObject(o.obj("extratags")?.toString() ?: "{}")
            tags.put("name", o.str("name") ?: return@mapNotNull null)
            tags.put(cls, o.str("type") ?: "")
            val a = o.obj("address")
            a.str("house_number")?.let { tags.put("addr:housenumber", it) }
            a.str("road")?.let { tags.put("addr:street", it) }
            (a.str("city") ?: a.str("town") ?: a.str("village"))?.let { tags.put("addr:city", it) }
            a.str("postcode")?.let { tags.put("addr:postcode", it) }
            toBusiness(tags, o.optDouble("lat"), o.optDouble("lon"), "${o.str("osm_type")}/${o.optLong("osm_id")}", here, cls)
        }.sortedBy { it.distanceM ?: Int.MAX_VALUE }
    }

    private fun toBusiness(tags: JSONObject, lat: Double, lon: Double, osmId: String, here: Place?, cls: String?): Business? {
        val name = tags.str("name") ?: return null
        val kind = tags.str("amenity") ?: tags.str("shop") ?: tags.str("tourism") ?: tags.str("leisure")
            ?: tags.str("office") ?: tags.str("craft") ?: tags.str("healthcare") ?: cls ?: ""
        val cuisine = tags.str("cuisine")?.replace(';', ',')?.replace('_', ' ')
        val category = prettyCategory(kind) + (cuisine?.let { " · ${it.split(',').take(2).joinToString(", ") { c -> c.trim().replaceFirstChar { ch -> ch.uppercase() } }}" } ?: "")
        val street = listOfNotNull(tags.str("addr:housenumber"), tags.str("addr:street")).joinToString(" ")
        val city = listOfNotNull(tags.str("addr:postcode"), tags.str("addr:city")).joinToString(" ")
        val address = listOf(street, city).filter { it.isNotBlank() }.joinToString(", ")
        val oh = tags.str("opening_hours")
        val status = oh?.let { OpeningHours.status(it) }
        return Business(
            name = name,
            category = category,
            lat = lat, lon = lon,
            address = address,
            phone = tags.str("phone") ?: tags.str("contact:phone"),
            website = tags.str("website") ?: tags.str("contact:website") ?: tags.str("url"),
            openingHours = oh,
            openNow = status?.first,
            hoursToday = status?.second,
            distanceM = here?.let { distance(it.lat, it.lon, lat, lon) },
            cuisine = cuisine,
            wheelchair = tags.str("wheelchair"),
            osmUrl = "https://www.openstreetmap.org/$osmId",
        )
    }

    private fun prettyCategory(k: String): String = when (k) {
        "restaurant" -> "Restaurant"
        "fast_food" -> "Fast food"
        "cafe" -> "Café"
        "bar" -> "Bar"
        "pub" -> "Pub"
        "bakery" -> "Bakery"
        "pastry" -> "Pastry shop"
        "pharmacy" -> "Pharmacy"
        "supermarket" -> "Supermarket"
        "convenience" -> "Convenience store"
        "hotel" -> "Hotel"
        "hostel" -> "Hostel"
        "guest_house" -> "Guest house"
        "hairdresser" -> "Hairdresser"
        "cinema" -> "Cinema"
        "fitness_centre" -> "Gym"
        "bank" -> "Bank"
        "atm" -> "ATM"
        "post_office" -> "Post office"
        "doctors" -> "Doctor"
        "dentist" -> "Dentist"
        "hospital" -> "Hospital"
        "car_repair" -> "Car repair"
        "fuel" -> "Gas station"
        "park" -> "Park"
        "museum" -> "Museum"
        "library" -> "Library"
        "books" -> "Bookshop"
        "florist" -> "Florist"
        "butcher" -> "Butcher"
        "veterinary" -> "Veterinarian"
        else -> k.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return (r * 2 * atan2(sqrt(a), sqrt(1 - a))).toInt()
    }

    fun formatDistance(m: Int?): String? = when {
        m == null -> null
        m < 1000 -> "$m m"
        else -> String.format(java.util.Locale.US, "%.1f km", m / 1000.0)
    }
}
