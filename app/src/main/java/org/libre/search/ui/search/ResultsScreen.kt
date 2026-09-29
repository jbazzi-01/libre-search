package org.libre.search.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libre.search.ui.LibreLogo
import org.libre.search.ui.Nav
import org.libre.search.ui.Screen
import org.libre.search.ui.SearchPill
import org.libre.search.ui.theme.LocalLibre

val resultTabs = listOf(
    "all" to "All", "images" to "Images", "videos" to "Videos", "news" to "News",
    "shopping" to "Shopping", "forums" to "Forums", "places" to "Places",
)

@Composable
fun ResultsScreen(s: ResultsState) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    LaunchedEffect(s.tab) {
        try {
            s.load(ctx)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            s.failed(e)
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text) }
            Box(Modifier.clickable { Nav.root(Screen.Home) }) { LibreLogo(22.sp) }
            Spacer(Modifier.width(10.dp))
            SearchPill(s.query, height = 46.dp, modifier = Modifier.weight(1f), onClick = {
                Nav.push(Screen.SearchInput(s.query, s.tab))
            })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(resultTabs) { (id, label) ->
                val sel = s.tab == id
                Column(
                    Modifier.clickable { s.tab = id }.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label, color = if (sel) c.text else c.textSecondary, fontSize = 16.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.width(if (sel) 32.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (sel) c.text else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = c.outline)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (s.tab) {
                "all" -> AllTab(s)
                "images" -> ImagesTab(s)
                "videos" -> VideosTab(s)
                "news" -> NewsResultsTab(s)
                "shopping" -> ShoppingTab(s)
                "forums" -> ForumsTab(s)
                "places" -> PlacesTab(s)
            }
        }
    }
}
