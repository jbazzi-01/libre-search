package org.libre.search.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.libre.search.core.Text as T
import org.libre.search.news.Article
import org.libre.search.news.NewsRepo
import org.libre.search.panels.TmdbHit
import org.libre.search.search.ImageResult
import org.libre.search.search.Product
import org.libre.search.search.VideoResult
import org.libre.search.ui.Favicon
import org.libre.search.ui.Label
import org.libre.search.ui.Loading
import org.libre.search.ui.Message
import org.libre.search.ui.Nav
import org.libre.search.ui.NetImage
import org.libre.search.ui.Screen
import org.libre.search.ui.ThickDivider
import org.libre.search.ui.ThinDivider
import org.libre.search.ui.cards.BusinessRow
import org.libre.search.ui.cards.CalculatorCard
import org.libre.search.ui.cards.MovieCard
import org.libre.search.ui.cards.PersonCard
import org.libre.search.ui.cards.PlacesCard
import org.libre.search.ui.cards.PlacesMap
import org.libre.search.ui.cards.WeatherCard
import org.libre.search.ui.cards.WikiCard
import org.libre.search.ui.theme.LocalLibre

@Composable
private fun KeyMissing(error: String) {
    val needsKey = error.contains("API key", true)
    Message(
        if (needsKey) "Web results need your Brave API key" else "Couldn't load web results",
        error,
        if (needsKey) "Open Settings" else null,
        if (needsKey) ({ Nav.push(Screen.Settings) }) else null,
    )
}

@Composable
fun AllTab(s: ResultsState) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val r = s.all
    if (r == null) { Loading(); return }
    val web = r.web
    val results = web?.results.orEmpty()
    val videos = web?.videos.orEmpty()
    val discussions = web?.discussions.orEmpty()
    val faq = web?.faq.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        web?.alteredQuery?.takeIf { !it.equals(r.query, true) }?.let { alt ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Showing results for", color = c.textSecondary, fontSize = 13.sp)
                    Text(alt, color = c.link, fontSize = 16.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)
                    Text(
                        "Search instead for ${r.query}", color = c.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.clickable { Nav.search("\"${r.query}\"") }
                    )
                }
            }
        }
        r.calculator?.let { calc -> item { CalculatorCard(calc); ThickDivider() } }
        r.weather?.let { w -> item { WeatherCard(w); ThickDivider() } }
        when (val sc = r.screen) {
            is TmdbHit.Work -> item { MovieCard(sc.work, r.screenWikiUrl); ThickDivider() }
            is TmdbHit.Person -> item { PersonCard(sc.person, r.screenWikiUrl, null); ThickDivider() }
            null -> r.wiki?.let { w -> item { WikiCard(w); ThickDivider() } }
        }
        if (r.places.isNotEmpty()) {
            item { PlacesCard(r.places, r.here) { s.tab = "places" }; ThickDivider() }
        }
        r.webError?.let { e -> if (results.isEmpty()) item { KeyMissing(e) } }

        itemsIndexed(results) { i, res ->
            if (i > 0) ThinDivider()
            WebResultItem(res)
            when (i) {
                1 -> if (r.topStories.isNotEmpty()) { ThickDivider(); TopStories(r.topStories) { s.tab = "news" }; ThickDivider() }
                3 -> if (videos.isNotEmpty()) { ThickDivider(); VideoCarousel(videos) { s.tab = "videos" }; ThickDivider() }
                5 -> if (discussions.isNotEmpty()) { ThickDivider(); DiscussionsBox(discussions) { s.tab = "forums" }; ThickDivider() }
                7 -> if (faq.isNotEmpty()) { ThickDivider(); FaqBox(faq); ThickDivider() }
            }
        }
        // Short result lists still get the extra boxes.
        if (results.size in 1..1 && r.topStories.isNotEmpty()) item { ThickDivider(); TopStories(r.topStories) { s.tab = "news" } }
        if (results.size in 1..3 && videos.isNotEmpty()) item { ThickDivider(); VideoCarousel(videos) { s.tab = "videos" } }
        if (results.size in 1..5 && discussions.isNotEmpty()) item { ThickDivider(); DiscussionsBox(discussions) { s.tab = "forums" } }

        itemsIndexed(s.morePages) { _, res -> ThinDivider(); WebResultItem(res) }

        if (results.isNotEmpty()) {
            item {
                if (!s.noMore) {
                    LaunchedEffect(s.morePages.size) { s.loadMore(ctx) }
                    Loading()
                } else {
                    Text(
                        "End of results", color = c.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
        }
        if (results.isEmpty() && r.webError == null) {
            item { Message("No web results for “${r.query}”", "Try different words or check the spelling.") }
        }
        item {
            Text(
                "Web results via Brave Search API · No AI answers",
                color = c.textSecondary, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()
            )
        }
    }
}

@Composable
fun ImagesTab(s: ResultsState) {
    val c = LocalLibre.current
    val list = s.images
    var open by remember { mutableStateOf<ImageResult?>(null) }
    when {
        list == null -> Loading()
        s.imagesError != null && list.isEmpty() -> KeyMissing(s.imagesError!!)
        list.isEmpty() -> Message("No images found", "AI-generated images are hidden (you can change this in Settings).")
        else -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list) { img ->
                Column(Modifier.clickable { open = img }) {
                    val ratio = if (img.width > 0 && img.height > 0) (img.width.toFloat() / img.height).coerceIn(0.5f, 2f) else 1f
                    NetImage(img.thumbnail, Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Favicon(img.pageUrl, null, 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(img.source, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(img.title, color = c.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                }
            }
        }
    }
    open?.let { img -> ImageViewer(img) { open = null } }
}

@Composable
private fun ImageViewer(img: ImageResult, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0000000))) {
            NetImage(img.imageUrl, Modifier.fillMaxSize().padding(vertical = 80.dp), ContentScale.Fit)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                Text(img.title, color = Color.White, fontSize = 15.sp, maxLines = 2)
                Text(img.source, color = Color(0xFFBDC1C6), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF8AB4F8)).clickable { onClose(); Nav.openUrl(ctx, img.pageUrl) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Visit", color = Color(0xFF062E6F), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color(0xFF062E6F), modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "Open image", color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))
                            .clickable { onClose(); Nav.openUrl(ctx, img.imageUrl, forceBrowser = true) }.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideosTab(s: ResultsState) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val list = s.videos
    when {
        list == null -> Loading()
        s.videosError != null && list.isEmpty() -> KeyMissing(s.videosError!!)
        list.isEmpty() -> Message("No videos found")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list) { v: VideoResult ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { Nav.openUrl(ctx, v.url) }, onLongClick = { menu = true })
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                            NetImage(v.thumbnail, Modifier.fillMaxSize())
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp))
                            v.duration?.let {
                                Text(
                                    it, color = Color.White, fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000)).padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = c.link, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(listOfNotNull(v.publisher, v.creator).distinct().joinToString(" · "), color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
                            v.age?.let { Text(it, color = c.textSecondary, fontSize = 12.sp) }
                        }
                    }
                    LinkMenu(menu, v.url, v.title) { menu = false }
                }
            }
        }
    }
}

@Composable
fun NewsResultsTab(s: ResultsState) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Article>?>(null) }
    LaunchedEffect(s.query) {
        NewsRepo.ensureFresh()
        list = NewsRepo.search(s.query)
    }
    val l = list
    when {
        l == null -> Loading()
        l.isEmpty() -> Message(
            "Nothing about “${s.query}” in your news sources",
            "Only your chosen outlets are searched here.",
            "Refresh sources"
        ) { scope.launch { NewsRepo.refresh(); list = NewsRepo.search(s.query) } }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(l) { a -> ArticleRow(a); ThinDivider() }
        }
    }
}

@Composable
fun ArticleRow(a: Article) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val src = NewsRepo.source(a.sourceId)
    Row(Modifier.fillMaxWidth().clickable { Nav.openUrl(ctx, a.url) }.padding(16.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Favicon(src?.site ?: a.url, null, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(src?.name ?: T.host(a.url), color = c.text, fontSize = 12.sp, maxLines = 1)
                src?.label?.let { Spacer(Modifier.width(6.dp)); Label(it, c.yellow) }
            }
            Spacer(Modifier.height(6.dp))
            Text(a.title, color = c.text, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(listOfNotNull(T.ago(a.time).ifBlank { null }, a.author).joinToString(" · "), color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
        }
        if (a.image != null) {
            Spacer(Modifier.width(12.dp))
            NetImage(a.image, Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
fun ShoppingTab(s: ResultsState) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val page = s.shopping
    when {
        page == null -> Loading()
        s.shoppingError != null && page.results.isEmpty() -> KeyMissing(s.shoppingError!!)
        page.results.isEmpty() -> Message("No shopping results")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            val products: List<Product> = page.products
            if (products.isNotEmpty()) {
                item {
                    Text("Products", color = c.text, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(((products.size + 1) / 2 * 250).dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false
                    ) {
                        items(products) { p ->
                            Column(
                                Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).clickable { Nav.openUrl(ctx, p.url) }.padding(10.dp)
                            ) {
                                NetImage(p.image, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), ContentScale.Fit)
                                Spacer(Modifier.height(6.dp))
                                Text(p.title, color = c.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                                p.price?.let { Text(it, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                                Text(p.store, color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
                                p.rating?.let { Text("★ $it", color = c.yellow, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            item { Text("Stores and reviews", color = c.text, fontSize = 20.sp, modifier = Modifier.padding(16.dp)) }
            items(page.results) { r -> WebResultItem(r); ThinDivider() }
        }
    }
}

@Composable
fun ForumsTab(s: ResultsState) {
    val page = s.forums
    when {
        page == null -> Loading()
        s.forumsError != null && page.results.isEmpty() && page.discussions.isEmpty() -> KeyMissing(s.forumsError!!)
        page.results.isEmpty() && page.discussions.isEmpty() -> Message("No forum threads found", "Tip: add “reddit” to your search.")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (page.discussions.isNotEmpty()) item { DiscussionsBox(page.discussions, null); ThickDivider() }
            items(page.results) { r -> WebResultItem(r); ThinDivider() }
        }
    }
}

@Composable
fun PlacesTab(s: ResultsState) {
    val list = s.places
    when {
        list == null -> Loading()
        list.isEmpty() -> Message(
            "No places found nearby",
            if (s.placesHere == null) "Allow location or set your city in Settings to search nearby places." else "OpenStreetMap has no match for this search around you.",
            "Search the web instead"
        ) { s.tab = "all" }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item { PlacesMap(list, s.placesHere, 280.dp, interactive = true) }
            items(list) { b -> BusinessRow(b); ThinDivider() }
            item {
                Text(
                    "Map and data © OpenStreetMap contributors", color = LocalLibre.current.textSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
