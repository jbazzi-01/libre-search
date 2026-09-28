package org.libre.search.panels

import org.libre.search.core.Http
import org.libre.search.core.Place
import org.libre.search.core.arr
import org.libre.search.core.obj
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

data class HourPoint(val label: String, val temp: Int, val code: Int, val isDay: Boolean, val rainChance: Int, val wind: Int)

data class DayPoint(
    val date: LocalDate,
    val label: String,
    val code: Int,
    val max: Int,
    val min: Int,
    val rainChance: Int,
    val sunrise: String,
    val sunset: String,
    val uv: Double,
    val windMax: Int,
)

data class WeatherData(
    val place: String,
    val temp: Int,
    val feelsLike: Int,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val wind: Int,
    val precipitation: Double,
    val hours: Map<LocalDate, List<HourPoint>>,
    val days: List<DayPoint>,
)

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Weather from Open-Meteo: free, no account, no tracking. */
object Weather {

    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun describe(code: Int, fr: Boolean): String = when (code) {
        0 -> if (fr) "Ensoleillé" else "Clear"
        1 -> if (fr) "Plutôt dégagé" else "Mostly clear"
        2 -> if (fr) "Partiellement nuageux" else "Partly cloudy"
        3 -> if (fr) "Nuageux" else "Mostly cloudy"
        45, 48 -> if (fr) "Brouillard" else "Fog"
        51, 53, 55 -> if (fr) "Bruine" else "Drizzle"
        56, 57 -> if (fr) "Bruine verglaçante" else "Freezing drizzle"
        61 -> if (fr) "Pluie faible" else "Light rain"
        63 -> if (fr) "Pluie" else "Rain"
        65 -> if (fr) "Forte pluie" else "Heavy rain"
        66, 67 -> if (fr) "Pluie verglaçante" else "Freezing rain"
        71 -> if (fr) "Neige faible" else "Light snow"
        73 -> if (fr) "Neige" else "Snow"
        75 -> if (fr) "Forte neige" else "Heavy snow"
        77 -> if (fr) "Grains de neige" else "Snow grains"
        80, 81 -> if (fr) "Averses" else "Showers"
        82 -> if (fr) "Fortes averses" else "Heavy showers"
        85, 86 -> if (fr) "Averses de neige" else "Snow showers"
        95 -> if (fr) "Orages" else "Thunderstorm"
        96, 99 -> if (fr) "Orages avec grêle" else "Thunderstorm with hail"
        else -> ""
    }

    suspend fun fetch(place: Place): WeatherData {
        val j = Http.getJson(
            Http.url(
                "https://api.open-meteo.com/v1/forecast",
                mapOf(
                    "latitude" to place.lat.toString(),
                    "longitude" to place.lon.toString(),
                    "current" to "temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m,precipitation",
                    "hourly" to "temperature_2m,weather_code,precipitation_probability,wind_speed_10m,is_day",
                    "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max,wind_speed_10m_max",
                    "timezone" to "auto",
                    "forecast_days" to "8",
                    "wind_speed_unit" to "kmh",
                )
            )
        )
        val c = j.obj("current")
        val h = j.obj("hourly")
        val times = h.arr("time")
        val now = LocalDateTime.now()
        val hours = LinkedHashMap<LocalDate, MutableList<HourPoint>>()
        if (times != null) {
            for (i in 0 until times.length()) {
                val t = try { LocalDateTime.parse(times.optString(i)) } catch (_: Exception) { continue }
                val isToday = t.toLocalDate() == now.toLocalDate()
                if (isToday && t.isBefore(now.withMinute(0).withSecond(0).withNano(0))) continue
                val list = hours.getOrPut(t.toLocalDate()) { mutableListOf() }
                val label = if (isToday && list.isEmpty()) "Now" else "%02d:00".format(t.hour)
                list += HourPoint(
                    label,
                    Math.round(h.arr("temperature_2m")!!.optDouble(i)).toInt(),
                    h.arr("weather_code")!!.optInt(i),
                    h.arr("is_day")!!.optInt(i) == 1,
                    h.arr("precipitation_probability")?.optInt(i) ?: 0,
                    Math.round(h.arr("wind_speed_10m")?.optDouble(i) ?: 0.0).toInt(),
                )
            }
        }
        val d = j.obj("daily")
        val dt = d.arr("time")
        val days = ArrayList<DayPoint>()
        if (dt != null) {
            for (i in 0 until dt.length()) {
                val date = LocalDate.parse(dt.optString(i))
                days += DayPoint(
                    date = date,
                    label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    code = d.arr("weather_code")!!.optInt(i),
                    max = Math.round(d.arr("temperature_2m_max")!!.optDouble(i)).toInt(),
                    min = Math.round(d.arr("temperature_2m_min")!!.optDouble(i)).toInt(),
                    rainChance = d.arr("precipitation_probability_max")?.optInt(i) ?: 0,
                    sunrise = d.arr("sunrise")?.optString(i)?.substringAfter('T') ?: "",
                    sunset = d.arr("sunset")?.optString(i)?.substringAfter('T') ?: "",
                    uv = d.arr("uv_index_max")?.optDouble(i) ?: 0.0,
                    windMax = Math.round(d.arr("wind_speed_10m_max")?.optDouble(i) ?: 0.0).toInt(),
                )
            }
        }
        return WeatherData(
            place = place.name,
            temp = Math.round(c?.optDouble("temperature_2m") ?: 0.0).toInt(),
            feelsLike = Math.round(c?.optDouble("apparent_temperature") ?: 0.0).toInt(),
            code = c?.optInt("weather_code") ?: 0,
            isDay = (c?.optInt("is_day") ?: 1) == 1,
            humidity = c?.optInt("relative_humidity_2m") ?: 0,
            wind = Math.round(c?.optDouble("wind_speed_10m") ?: 0.0).toInt(),
            precipitation = c?.optDouble("precipitation") ?: 0.0,
            hours = hours,
            days = days,
        )
    }
}
