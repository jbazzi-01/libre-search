package org.libre.search.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libre.search.core.Prefs
import org.libre.search.panels.HourPoint
import org.libre.search.panels.Weather
import org.libre.search.panels.WeatherData
import org.libre.search.ui.Nav
import org.libre.search.ui.theme.LocalLibre

@Composable
fun WeatherCard(w: WeatherData) {
    val c = LocalLibre.current
    val fr = Prefs.panelLang == "fr"
    var dayIdx by remember { mutableIntStateOf(0) }
    var openRow by remember { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var chooseArea by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val day = w.days.getOrNull(dayIdx)
    val hours: List<HourPoint> = day?.let { w.hours[it.date] }.orEmpty()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(10.dp))
            Text(w.place.ifBlank { "Your area" }, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(" · ", color = c.textSecondary, fontSize = 16.sp)
            Text("Choose area", color = c.link, fontSize = 16.sp, modifier = Modifier.clickable { chooseArea = true })
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Weather", color = c.text, fontSize = 28.sp, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = c.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Change area") }, onClick = { menu = false; chooseArea = true })
                    DropdownMenuItem(text = { Text("Data: Open-Meteo") }, onClick = { menu = false })
                }
            }
        }

        // Main card
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp)).background(c.surface2)
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                val isToday = dayIdx == 0
                Text(if (isToday) "Now" else day?.date?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) ?: "", color = c.text, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isToday) {
                        Text("${w.temp}°", color = c.text, fontSize = 64.sp, fontWeight = FontWeight.Normal)
                        WeatherIcon(w.code, w.isDay, Modifier.size(64.dp))
                    } else if (day != null) {
                        Text("${day.max}°", color = c.text, fontSize = 64.sp)
                        Text(" / ${day.min}°", color = c.textSecondary, fontSize = 26.sp)
                        Spacer(Modifier.width(8.dp))
                        WeatherIcon(day.code, true, Modifier.size(60.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        val code = if (isToday) w.code else day?.code ?: 0
                        Text(Weather.describe(code, fr), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        if (isToday) Text("Feels like ${w.feelsLike}°", color = c.text, fontSize = 14.sp)
                        else if (day != null) Text("Rain ${day.rainChance}%", color = c.text, fontSize = 14.sp)
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(hours) { hp ->
                    Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${hp.temp}°", color = c.text, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        WeatherIcon(hp.code, hp.isDay, Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(hp.label, color = c.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            val sceneCode = if (dayIdx == 0) w.code else day?.code ?: w.code
            WeatherScene(sceneCode, if (dayIdx == 0) w.isDay else true, Modifier.fillMaxWidth().height(96.dp))
        }

        // Day chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(w.days.size) { i ->
                val d = w.days[i]
                val sel = i == dayIdx
                Column(
                    Modifier.width(82.dp).clip(RoundedCornerShape(16.dp))
                        .background(c.surface2)
                        .then(if (sel) Modifier.border(2.dp, c.text, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable { dayIdx = i }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (i == 0) "Today" else d.label, color = c.text, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    WeatherIcon(d.code, true, Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("${d.max}°/${d.min}°", color = c.text, fontSize = 14.sp)
                }
            }
        }

        // Expandable details
        val rainNow = hours.firstOrNull()?.rainChance ?: day?.rainChance ?: 0
        DetailRow(Icons.Default.Umbrella, "Precipitation", "$rainNow%", openRow == "rain") {
            openRow = if (openRow == "rain") null else "rain"
        }
        if (openRow == "rain") Bars(hours.map { it.rainChance }, hours.map { it.label }, 100, "%")
        val windNow = if (dayIdx == 0) w.wind else day?.windMax ?: 0
        DetailRow(Icons.Default.Air, "Wind", "$windNow km/h", openRow == "wind") {
            openRow = if (openRow == "wind") null else "wind"
        }
        if (openRow == "wind") Bars(hours.map { it.wind }, hours.map { it.label }, (hours.maxOfOrNull { it.wind } ?: 10).coerceAtLeast(10), " km/h")

        if (showMore && day != null) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (dayIdx == 0) Fact("Humidity", "${w.humidity}%")
                Fact("UV index", String.format(java.util.Locale.US, "%.0f", day.uv) + uvLabel(day.uv))
                Fact("Sunrise", day.sunrise)
                Fact("Sunset", day.sunset)
                Fact("Chance of rain", "${day.rainChance}%")
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable { showMore = !showMore }.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (showMore) "Show less" else "Show more", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Icon(if (showMore) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = c.text)
        }
    }

    if (chooseArea) {
        var city by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chooseArea = false },
            title = { Text("Choose area") },
            text = { OutlinedTextField(city, { city = it }, singleLine = true, placeholder = { Text("City") }) },
            confirmButton = {
                TextButton(onClick = {
                    chooseArea = false
                    if (city.isNotBlank()) Nav.search("weather ${city.trim()}")
                }) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { chooseArea = false }) { Text("Cancel") } }
        )
    }
}

private fun uvLabel(uv: Double) = when {
    uv < 3 -> " (low)"
    uv < 6 -> " (moderate)"
    uv < 8 -> " (high)"
    else -> " (very high)"
}

@Composable
private fun Fact(k: String, v: String) {
    val c = LocalLibre.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, color = c.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(v, color = c.text, fontSize = 15.sp)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, title: String, value: String, open: Boolean, onClick: () -> Unit) {
    val c = LocalLibre.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.link)
        }
        Spacer(Modifier.width(16.dp))
        Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("  ·  $value", color = c.textSecondary, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = c.text)
    }
}

@Composable
private fun Bars(values: List<Int>, labels: List<String>, max: Int, unit: String) {
    val c = LocalLibre.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(values.size) { i ->
            Column(Modifier.width(52.dp).height(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${values[i]}${if (unit == "%") "%" else ""}", color = c.text, fontSize = 12.sp)
                Box(Modifier.weight(1f).width(20.dp), contentAlignment = Alignment.BottomCenter) {
                    val frac = (values[i].toFloat() / max).coerceIn(0.03f, 1f)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(6.dp)).background(c.link))
                }
                Text(labels.getOrElse(i) { "" }, color = c.textSecondary, fontSize = 11.sp)
            }
        }
    }
}
