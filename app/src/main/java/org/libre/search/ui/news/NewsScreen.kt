package org.libre.search.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.libre.search.news.NewsRepo
import org.libre.search.news.Sources
import org.libre.search.news.Tendency
import org.libre.search.ui.Nav
import org.libre.search.ui.Screen
import org.libre.search.ui.home.DiscoverCard
import org.libre.search.ui.home.NewsFilter
import org.libre.search.ui.search.ArticleRow
import org.libre.search.ui.ThinDivider
import org.libre.search.ui.theme.LocalLibre

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen() {
    val c = LocalLibre.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { NewsRepo.ensureFresh() } catch (_: Exception) {} }

    val filters = buildList {
        add("all" to "All")
        add("good" to "Good news")
        add(Tendency.COMMUNIST.name to "Communist")
        add(Tendency.ANARCHIST.name to "Anarchist")
        add(Tendency.LEFT.name to "Left")
        add(Tendency.PALESTINE.name to "Palestine")
        add("fr" to "Français")
        add("en" to "English")
        Sources.enabled().forEach { add("src:" + it.id to it.name) }
    }
    val sel = NewsFilter.selected
    val list = when {
        sel == "all" -> NewsRepo.articles.filter { NewsRepo.source(it.sourceId)?.tendency != Tendency.GOOD }
        sel == "good" -> NewsRepo.goodNews()
        sel == "fr" || sel == "en" -> NewsRepo.articles.filter { NewsRepo.source(it.sourceId)?.lang == sel }
        sel.startsWith("src:") -> NewsRepo.articles.filter { it.sourceId == sel.removePrefix("src:") }
        else -> NewsRepo.articles.filter { NewsRepo.source(it.sourceId)?.tendency?.name == sel }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("News", color = c.text, fontSize = 26.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { try { NewsRepo.refresh() } catch (_: Exception) {} } }) {
                Icon(Icons.Default.Refresh, null, tint = c.text)
            }
            IconButton(onClick = { Nav.push(Screen.Settings) }) { Icon(Icons.Default.Tune, null, tint = c.text) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { (id, label) ->
                val on = id == sel
                Text(
                    label, color = if (on) (if (c.isDark) androidx.compose.ui.graphics.Color(0xFF062E6F) else androidx.compose.ui.graphics.Color.White) else c.text,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accent else c.chip)
                        .clickable { NewsFilter.selected = id }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        if (NewsRepo.failing.isNotEmpty()) {
            val names = NewsRepo.failing.mapNotNull { NewsRepo.source(it)?.name }
            if (names.isNotEmpty()) Text(
                "Couldn't reach: " + names.joinToString(", "),
                color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        PullToRefreshBox(
            isRefreshing = NewsRepo.loading.value,
            onRefresh = { scope.launch { try { NewsRepo.refresh() } catch (_: Exception) {} } },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (list.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            if (NewsRepo.loading.value) "Loading your sources…"
                            else if (sel == "good") "No good news found yet. Pull to refresh."
                            else "Nothing here yet. Pull to refresh.",
                            color = c.textSecondary, modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list.take(4), key = { "top" + it.url }) { a -> DiscoverCard(a) }
                    items(list.drop(4).take(300), key = { it.url }) { a -> ArticleRow(a); ThinDivider() }
                }
            }
        }
    }
}
