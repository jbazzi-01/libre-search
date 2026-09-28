package org.libre.search.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libre.search.panels.CastMember
import org.libre.search.panels.KnownFor
import org.libre.search.panels.PersonInfo
import org.libre.search.panels.ScreenWork
import org.libre.search.panels.WikiPanel
import org.libre.search.search.Calculator
import org.libre.search.ui.Nav
import org.libre.search.ui.NetImage
import org.libre.search.ui.PillButton
import org.libre.search.ui.theme.LocalLibre
import java.text.NumberFormat
import java.util.Locale

@Composable
private fun PanelTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalLibre.current
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tabs) { t ->
            Column(Modifier.clickable { onSelect(t) }.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t, color = if (t == selected) c.text else c.textSecondary, fontSize = 15.sp, fontWeight = if (t == selected) FontWeight.Medium else FontWeight.Normal)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (t == selected) c.text else Color.Transparent))
            }
        }
    }
    HorizontalDivider(color = c.outline)
}

@Composable
private fun InfoRow(k: String, v: String, onClick: (() -> Unit)? = null) {
    val c = LocalLibre.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text("$k: ", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            v, color = if (onClick != null) c.link else c.textSecondary, fontSize = 14.sp,
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    }
}

@Composable
private fun WikiLink(url: String?) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    if (url == null) return
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(50)).background(c.chip)
            .clickable { Nav.openUrl(ctx, url) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text("W", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text("Wikipedia", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun money(v: Long) = "$" + NumberFormat.getInstance(Locale.US).format(v)

@Composable
fun MovieCard(m: ScreenWork, wikiUrl: String?) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("Overview") }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(m.title, color = c.text, fontSize = 30.sp, lineHeight = 36.sp)
            val sub = listOfNotNull(
                m.year, m.genres.take(2).joinToString("/").ifBlank { null },
                m.runtime ?: m.seasons?.let { "$it season${if (it > 1) "s" else ""}" },
                m.certification?.ifBlank { null }
            ).joinToString(" ‧ ")
            Text((if (m.isTv) "TV series ‧ " else "") + sub, color = c.textSecondary, fontSize = 14.sp)
        }
        PanelTabs(listOf("Overview", "Cast", "Watch"), tab) { tab = it }
        when (tab) {
            "Overview" -> {
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { NetImage(m.poster, Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))) }
                    if (m.backdrop != null) item { NetImage(m.backdrop, Modifier.width(320.dp).height(180.dp).clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))) }
                }
                if (m.rating != null) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(Locale.US, "%.1f/10", m.rating), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text("TMDB · ${NumberFormat.getInstance(Locale.US).format(m.voteCount)} votes", color = c.textSecondary, fontSize = 13.sp)
                    }
                }
                if (m.tagline != null) Text("“${m.tagline}”", color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                if (m.overview.isNotBlank()) {
                    Text(m.overview, color = c.text, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                WikiLink(wikiUrl)
                Spacer(Modifier.height(6.dp))
                m.releaseDate?.let { InfoRow(if (m.isTv) "First aired" else "Release date", it) }
                if (m.directors.isNotEmpty()) InfoRow(if (m.isTv) "Created by" else "Director", m.directors.joinToString(", ")) {
                    Nav.search(m.directors.first())
                }
                if (m.writers.isNotEmpty()) InfoRow("Screenplay", m.writers.joinToString(", "))
                m.boxOffice?.let { InfoRow("Box office", money(it)) }
                m.budget?.let { InfoRow("Budget", money(it)) }
                if (m.networks.isNotEmpty()) InfoRow(if (m.isTv) "Network" else "Production", m.networks.joinToString(", "))
                m.originalTitle?.takeIf { it != m.title }?.let { InfoRow("Original title", it) }
                Spacer(Modifier.height(8.dp))
                CastRow(m.cast)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.trailerUrl?.let { t ->
                        PillButton("Trailer", icon = { Icon(Icons.Default.PlayArrow, null, tint = c.link, modifier = Modifier.size(18.dp)) }) {
                            Nav.openUrl(ctx, t)
                        }
                    }
                    m.imdbId?.let { id -> PillButton("IMDb") { Nav.openUrl(ctx, "https://www.imdb.com/title/$id/") } }
                    PillButton("TMDB") { Nav.openUrl(ctx, "https://www.themoviedb.org/${if (m.isTv) "tv" else "movie"}/${m.id}") }
                }
            }
            "Cast" -> {
                Column(Modifier.padding(vertical = 8.dp)) {
                    m.cast.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { Nav.search(p.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NetImage(p.photo, Modifier.size(52.dp).clip(CircleShape), fallback = { Icon(Icons.Default.Person, null, tint = c.textSecondary) })
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(p.name, color = c.text, fontSize = 15.sp)
                                if (p.role.isNotBlank()) Text(p.role, color = c.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                    if (m.directors.isNotEmpty()) {
                        Text(if (m.isTv) "Created by" else "Directed by", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
                        m.directors.forEach { d ->
                            Text(d, color = c.link, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { Nav.search(d) }.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            "Watch" -> {
                Column(Modifier.padding(16.dp)) {
                    if (m.watchProviders.isEmpty()) Text("No streaming info for your country.", color = c.textSecondary, fontSize = 14.sp)
                    else {
                        Text("Available on", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        m.watchProviders.forEach { Text("• $it", color = c.text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text("Streaming data from JustWatch via TMDB", color = c.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<CastMember>) {
    val c = LocalLibre.current
    if (cast.isEmpty()) return
    Text("Cast", color = c.text, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cast.take(15)) { p ->
            Column(Modifier.width(96.dp).clickable { Nav.search(p.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                NetImage(p.photo, Modifier.size(88.dp).clip(CircleShape), fallback = { Icon(Icons.Default.Person, null, tint = c.textSecondary) })
                Spacer(Modifier.height(6.dp))
                Text(p.name, color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                Text(p.role, color = c.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun PersonCard(p: PersonInfo, wikiUrl: String?, wiki: WikiPanel?) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("Overview") }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = c.text, fontSize = 30.sp, lineHeight = 36.sp)
                val role = when (p.department) {
                    "Acting" -> "Actor"
                    "Directing" -> "Film director"
                    "Writing" -> "Screenwriter"
                    "Production" -> "Producer"
                    "Sound" -> "Composer"
                    else -> p.department
                }
                if (role != null) Text(wiki?.description ?: role, color = c.textSecondary, fontSize = 14.sp)
            }
            NetImage(p.photo, Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)), fallback = { Icon(Icons.Default.Person, null, tint = c.textSecondary) })
        }
        PanelTabs(listOf("Overview", "Films & shows"), tab) { tab = it }
        if (tab == "Overview") {
            val bio = wiki?.extract?.ifBlank { null } ?: p.bio
            if (bio.isNotBlank()) Text(bio.take(600) + if (bio.length > 600) "…" else "", color = c.text, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(16.dp))
            WikiLink(wikiUrl ?: wiki?.url)
            Spacer(Modifier.height(6.dp))
            p.birthday?.let { InfoRow("Born", it + (p.birthplace?.let { bp -> ", $bp" } ?: "")) }
            p.deathday?.let { InfoRow("Died", it) }
            wiki?.facts?.filter { it.first !in setOf("Born", "Naissance", "Died", "Décès", "Place of birth", "Lieu de naissance") }?.take(5)?.forEach { (k, v) -> InfoRow(k, v) }
            Spacer(Modifier.height(8.dp))
            KnownForRow(p.knownFor)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.imdbId?.let { id -> PillButton("IMDb") { Nav.openUrl(ctx, "https://www.imdb.com/name/$id/") } }
                PillButton("TMDB") { Nav.openUrl(ctx, "https://www.themoviedb.org/person/${p.id}") }
                p.instagram?.let { ig -> PillButton("Instagram") { Nav.openUrl(ctx, "https://www.instagram.com/$ig/") } }
            }
        } else {
            Column(Modifier.padding(vertical = 8.dp)) {
                p.knownFor.forEach { k ->
                    Row(Modifier.fillMaxWidth().clickable { Nav.search(k.title + (k.year?.let { " $it" } ?: "")) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetImage(k.poster, Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(k.title, color = c.text, fontSize = 15.sp)
                            Text(listOfNotNull(k.year, if (k.isTv) "TV series" else "Film").joinToString(" · "), color = c.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnownForRow(list: List<KnownFor>) {
    val c = LocalLibre.current
    if (list.isEmpty()) return
    Text("Films & shows", color = c.text, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { k ->
            Column(Modifier.width(104.dp).clickable { Nav.search(k.title + (k.year?.let { " $it" } ?: "")) }) {
                NetImage(k.poster, Modifier.width(104.dp).height(156.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(6.dp))
                Text(k.title, color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                k.year?.let { Text(it, color = c.textSecondary, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun WikiCard(w: WikiPanel) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.title, color = c.text, fontSize = 28.sp, lineHeight = 34.sp)
                w.description?.let { Text(it.replaceFirstChar { ch -> ch.uppercase() }, color = c.textSecondary, fontSize = 14.sp) }
            }
            if (w.image != null) {
                Spacer(Modifier.width(12.dp))
                NetImage(w.image, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
            }
        }
        HorizontalDivider(color = c.outline)
        if (w.extract.isNotBlank()) {
            Text(w.extract, color = c.text, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
        WikiLink(w.url)
        Spacer(Modifier.height(4.dp))
        w.facts.take(8).forEach { (k, v) -> InfoRow(k, v) }
        if (w.website != null || w.socials.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                w.website?.let { site -> item { PillButton("Website") { Nav.openUrl(ctx, site) } } }
                items(w.socials) { (name, url) -> PillButton(name) { Nav.openUrl(ctx, url) } }
            }
        }
    }
}

@Composable
fun CalculatorCard(a: Calculator.Answer) {
    val c = LocalLibre.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Calculate, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(a.expression, color = c.textSecondary, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(a.result, color = c.text, fontSize = 40.sp)
    }
}
