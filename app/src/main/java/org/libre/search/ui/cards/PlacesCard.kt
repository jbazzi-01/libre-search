package org.libre.search.ui.cards

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.libre.search.core.Place
import org.libre.search.panels.Business
import org.libre.search.panels.Places
import org.libre.search.ui.Nav
import org.libre.search.ui.PillButton
import org.libre.search.ui.SectionTitle
import org.libre.search.ui.ThinDivider
import org.libre.search.ui.theme.LocalLibre
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

fun openDirections(ctx: Context, b: Business) {
    val uri = Uri.parse("geo:${b.lat},${b.lon}?q=${b.lat},${b.lon}(${Uri.encode(b.name)})")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Nav.openUrl(ctx, "https://www.openstreetmap.org/directions?to=${b.lat},${b.lon}", forceBrowser = true)
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun PlacesMap(list: List<Business>, here: Place?, height: Dp, interactive: Boolean, onMarker: ((Business) -> Unit)? = null) {
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                if (!interactive) setOnTouchListener { _, _ -> true }
                isTilesScaledToDpi = true
            }
        },
        update = { map ->
            map.overlays.clear()
            list.take(30).forEach { b ->
                val m = Marker(map)
                m.position = GeoPoint(b.lat, b.lon)
                m.title = b.name
                m.snippet = b.category
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                if (onMarker != null) m.setOnMarkerClickListener { _, _ -> onMarker(b); true }
                map.overlays.add(m)
            }
            val points = list.take(30).map { GeoPoint(it.lat, it.lon) } + listOfNotNull(here?.let { GeoPoint(it.lat, it.lon) })
            if (points.size >= 2) {
                map.post {
                    try {
                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.3f), false)
                    } catch (_: Exception) {}
                }
            } else if (points.size == 1) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(points[0])
            }
            map.invalidate()
        },
        modifier = Modifier.fillMaxWidth().height(height)
    )
}

@Composable
fun BusinessRow(b: Business) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clickable { Nav.openUrl(ctx, b.website ?: b.osmUrl) }.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(b.name, color = c.link, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(
            listOfNotNull(b.category, Places.formatDistance(b.distanceM)).joinToString(" · "),
            color = c.textSecondary, fontSize = 13.sp
        )
        if (b.address.isNotBlank()) Text(b.address, color = c.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (b.openNow != null) {
            Row {
                Text(if (b.openNow) "Open" else "Closed", color = if (b.openNow) c.green else c.red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                b.hoursToday?.let { Text(" · $it", color = c.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        } else if (b.openingHours != null) {
            Text(b.openingHours, color = c.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.wheelchair == "yes") Text("♿ Wheelchair accessible", color = c.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PillButton("Directions", icon = { Icon(Icons.Default.Directions, null, tint = c.link, modifier = Modifier.size(16.dp)) }) { openDirections(ctx, b) } }
            b.phone?.let { ph ->
                item {
                    PillButton("Call", icon = { Icon(Icons.Default.Call, null, tint = c.link, modifier = Modifier.size(16.dp)) }) {
                        try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.replace(" ", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                    }
                }
            }
            b.website?.let { site ->
                item { PillButton("Website", icon = { Icon(Icons.Default.Language, null, tint = c.link, modifier = Modifier.size(16.dp)) }) { Nav.openUrl(ctx, site) } }
            }
            item { PillButton("Map", icon = { Icon(Icons.Default.Map, null, tint = c.link, modifier = Modifier.size(16.dp)) }) { Nav.openUrl(ctx, b.osmUrl, forceBrowser = true) } }
        }
    }
}

@Composable
fun PlacesCard(list: List<Business>, here: Place?, onMore: () -> Unit) {
    val c = LocalLibre.current
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Places")
        Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onMore)) {
            PlacesMap(list.take(10), here, 170.dp, interactive = false)
        }
        list.take(3).forEachIndexed { i, b ->
            if (i > 0) ThinDivider()
            BusinessRow(b)
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onMore).padding(14.dp),
            horizontalArrangement = Arrangement.Center
        ) { Text("More places", color = c.link, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        Text(
            "Data © OpenStreetMap contributors", color = c.textSecondary, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
