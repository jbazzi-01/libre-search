package org.libre.search.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.libre.search.core.LocationHelper
import org.libre.search.core.Text as T
import org.libre.search.news.Article
import org.libre.search.news.NewsRepo
import org.libre.search.panels.Weather
import org.libre.search.panels.WeatherData
import org.libre.search.ui.Favicon
import org.libre.search.ui.Label
import org.libre.search.ui.LibreLogo
import org.libre.search.ui.Nav
import org.libre.search.ui.NetImage
import org.libre.search.ui.Screen
import org.libre.search.ui.SearchPill
import org.libre.search.ui.cards.WeatherIcon
import org.libre.search.ui.theme.LocalLibre

object HomeCache {
    var weather by mutableStateOf<WeatherData?>(null)
    var weatherTime = 0L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (HomeCache.weather == null || System.currentTimeMillis() - HomeCache.weatherTime > 30 * 60 * 1000) {
            try {
                LocationHelper.current(ctx)?.let {
                    HomeCache.weather = Weather.fetch(it)
                    HomeCache.weatherTime = System.currentTimeMillis()
                }
            } catch (_: Exception) {}
        }
    }
    LaunchedEffect(Unit) { try { NewsRepo.ensureFresh() } catch (_: Exception) {} }

    val feed: List<Article> = NewsRepo.articles.take(60)

    PullToRefreshBox(
        isRefreshing = NewsRepo.loading.value,
        onRefresh = { scope.launch { try { NewsRepo.refresh() } catch (_: Exception) {} } },
        modifier = Modifier.fillMaxSize().background(c.bg)
    ) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { Nav.push(Screen.Settings) }) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface2), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, null, tint = c.text, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LibreLogo(52.sp)
                }
            }
            item {
                SearchPill(
                    "", "Search", Modifier.padding(horizontal = 20.dp), height = 56.dp,
                    onClick = { Nav.push(Screen.SearchInput("")) },
                    trailing = { Icon(Icons.Default.Mic, null, tint = c.accent) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeCache.weather?.let { w ->
                        item {
                            Row(
                                Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface2)
                                    .clickable { Nav.search("weather") }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WeatherIcon(w.code, w.isDay, Modifier.size(24.dp))
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text("${w.temp}°", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(w.place, color = c.textSecondary, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    item { QuickChip("Good news") { Nav.root(Screen.News); NewsFilter.selected = "good" } }
                    item { QuickChip("News") { Nav.root(Screen.News) } }
                    item { QuickChip("Tabs (${org.libre.search.browser.Tabs.tabs.size})") { Nav.push(Screen.TabSwitcher) } }
                }
            }
            item {
                Text("Discover", color = c.text, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            if (feed.isEmpty()) {
                item {
                    Text(
                        if (NewsRepo.loading.value) "Loading your sources…" else "Pull down to load news from your sources.",
                        color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(20.dp)
                    )
                }
            }
            items(feed, key = { it.url }) { a -> DiscoverCard(a) }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    val c = LocalLibre.current
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = c.text, fontSize = 14.sp) }
}

@Composable
fun DiscoverCard(a: Article) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val src = NewsRepo.source(a.sourceId)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(c.surface).clickable { Nav.openUrl(ctx, a.url) }
    ) {
        if (a.image != null) NetImage(a.image, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(16.dp)) {
            Text(a.title, color = c.text, fontSize = 18.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (a.image == null && a.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(a.summary, color = c.textSecondary, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Favicon(src?.site ?: a.url, null, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    listOfNotNull(src?.name, T.ago(a.time).ifBlank { null }).joinToString(" · "),
                    color = c.textSecondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false)
                )
                src?.label?.let { Spacer(Modifier.width(6.dp)); Label(it, c.yellow) }
            }
        }
    }
}

/** Remembers the News screen's selected filter across navigation. */
object NewsFilter {
    var selected by mutableStateOf("all")
}
