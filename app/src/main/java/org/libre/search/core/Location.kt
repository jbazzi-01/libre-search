package org.libre.search.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.round

data class Place(val lat: Double, val lon: Double, val name: String)

object LocationHelper {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Rounds to 2 decimals (about 1 km) so precise position never leaves the phone. */
    private fun r(x: Double) = round(x * 100.0) / 100.0

    /** Best-effort current place: manual city, then GPS/network, then last known. */
    suspend fun current(ctx: Context): Place? {
        if (Prefs.locationMode == "manual" && !Prefs.manualLat.isNaN()) {
            return Place(Prefs.manualLat, Prefs.manualLon, Prefs.manualCity)
        }
        val loc = if (hasPermission(ctx)) deviceLocation(ctx) else null
        if (loc != null) {
            val lat = r(loc.latitude)
            val lon = r(loc.longitude)
            val sameAsBefore = !Prefs.lastLat.isNaN() &&
                kotlin.math.abs(Prefs.lastLat - lat) < 0.02 && kotlin.math.abs(Prefs.lastLon - lon) < 0.02
            val name = if (sameAsBefore && Prefs.lastCity.isNotBlank()) Prefs.lastCity else reverse(lat, lon)
            Prefs.lastLat = lat
            Prefs.lastLon = lon
            if (name.isNotBlank()) Prefs.lastCity = name
            return Place(lat, lon, name.ifBlank { Prefs.lastCity })
        }
        if (!Prefs.lastLat.isNaN()) return Place(Prefs.lastLat, Prefs.lastLon, Prefs.lastCity)
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun deviceLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val known = providers.mapNotNull { p ->
            try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
        }.maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < 30 * 60 * 1000) return known
        if (Build.VERSION.SDK_INT >= 30) {
            val provider = providers.firstOrNull { p ->
                try { lm.isProviderEnabled(p) } catch (_: Exception) { false }
            } ?: return known
            val fresh = withTimeoutOrNull(6000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { l ->
                            if (cont.isActive) cont.resume(l)
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            return fresh ?: known
        }
        return known
    }

    suspend fun reverse(lat: Double, lon: Double): String = try {
        val j = Http.getJson(
            Http.url(
                "https://nominatim.openstreetmap.org/reverse",
                mapOf("lat" to lat.toString(), "lon" to lon.toString(), "format" to "jsonv2", "zoom" to "10")
            )
        )
        val a = j.obj("address")
        a.str("city") ?: a.str("town") ?: a.str("village") ?: a.str("municipality") ?: j.str("name") ?: ""
    } catch (_: Exception) {
        ""
    }

    /** Geocodes a city name with Open-Meteo. */
    suspend fun geocode(name: String): Place? = try {
        val j = Http.getJson(
            Http.url(
                "https://geocoding-api.open-meteo.com/v1/search",
                mapOf("name" to name, "count" to "1", "language" to Prefs.panelLang, "format" to "json")
            )
        )
        j.arr("results").objects().firstOrNull()?.let {
            Place(it.optDouble("latitude"), it.optDouble("longitude"), it.str("name") ?: name)
        }
    } catch (_: Exception) {
        null
    }
}
