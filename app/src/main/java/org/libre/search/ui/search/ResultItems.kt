package org.libre.search.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.libre.search.core.Prefs
import org.libre.search.core.Text as T
import org.libre.search.news.Article
import org.libre.search.news.NewsRepo
import org.libre.search.search.Discussion
import org.libre.search.search.Faq
import org.libre.search.search.VideoResult
import org.libre.search.search.WebResult
import org.libre.search.ui.Favicon
import org.libre.search.ui.Label
import org.libre.search.ui.Nav
import org.libre.search.ui.NetImage
import org.libre.search.ui.SectionTitle
import org.libre.search.ui.ThinDivider
import org.libre.search.ui.theme.LocalLibre

fun copyLink(ctx: Context, url: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
}

fun shareLink(ctx: Context, url: String, title: String = "") {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    if (title.isNotBlank()) i.putExtra(Intent.EXTRA_SUBJECT, title)
    ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Long-press menu shared by every result type. */
@Composable
fun LinkMenu(expanded: Boolean, url: String, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Open in new tab") }, onClick = { onDismiss(); Nav.openUrl(ctx, url, forceBrowser = true) })
        DropdownMenuItem(text = { Text("Open in private tab") }, onClick = { onDismiss(); Nav.openUrl(ctx, url, forceBrowser = true, private = true) })
        DropdownMenuItem(text = { Text("Copy link") }, onClick = { onDismiss(); copyLink(ctx, url) })
        DropdownMenuItem(text = { Text("Share") }, onClick = { onDismiss(); shareLink(ctx, url, title) })
        DropdownMenuItem(text = { Text("Never show ${T.host(url)}") }, onClick = {
            onDismiss()
            Prefs.blockedDomains = Prefs.blockedDomains + T.host(url)
            Toast.makeText(ctx, "${T.host(url)} blocked. Undo in Settings.", Toast.LENGTH_SHORT).show()
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebResultItem(r: WebResult) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { Nav.openUrl(ctx, r.url) }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Favicon(r.url, r.favicon)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.siteName, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(T.breadcrumb(r.url), color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(r.title, color = c.link, fontSize = 19.sp, lineHeight = 25.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val desc = buildString {
                        if (!r.age.isNullOrBlank()) append(r.age).append(" — ")
                        append(r.description)
                    }
                    if (desc.isNotBlank()) Text(desc, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (r.price != null || r.rating != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(r.rating?.let { "★ $it" }, r.price).joinToString(" · "),
                            color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (r.thumbnail != null) {
                    Spacer(Modifier.width(12.dp))
                    NetImage(r.thumbnail, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)))
                }
            }
            if (r.sitelinks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(r.sitelinks) { (t, u) ->
                        Text(
                            t, color = c.link, fontSize = 13.sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.chip)
                                .clickable { Nav.openUrl(ctx, u) }.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
        LinkMenu(menu, r.url, r.title) { menu = false }
    }
}

@Composable
fun VideoCarousel(videos: List<VideoResult>, onMore: (() -> Unit)?) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    Column {
        SectionTitle("Videos")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos.take(10)) { v ->
                Column(Modifier.width(240.dp).clickable { Nav.openUrl(ctx, v.url) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                        NetImage(v.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Box(
                            Modifier.align(Alignment.Center).size(40.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
                        if (!v.duration.isNullOrBlank()) {
                            Text(
                                v.duration, color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(v.title, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    Text(
                        listOfNotNull(v.publisher, v.creator, v.age).distinct().joinToString(" · "),
                        color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (onMore != null) MoreRow("More videos", onMore)
    }
}

@Composable
fun MoreRow(text: String, onClick: () -> Unit) {
    val c = LocalLibre.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(50))
            .background(c.chip).clickable(onClick = onClick).padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = c.text, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun DiscussionsBox(list: List<Discussion>, onMore: (() -> Unit)?) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    Column {
        SectionTitle("Discussions and forums")
        list.take(5).forEachIndexed { i, d ->
            if (i > 0) ThinDivider()
            Column(Modifier.fillMaxWidth().clickable { Nav.openUrl(ctx, d.url) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Favicon(d.url, null, 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOfNotNull(d.forum, d.age).joinToString(" · "),
                        color = c.textSecondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(d.title, color = c.link, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (d.snippet.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(d.snippet, color = c.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (d.answers != null || d.score != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            listOfNotNull(d.answers?.let { "$it answers" }, d.score?.let { "$it votes" }).joinToString(" · "),
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }
            }
        }
        if (onMore != null) MoreRow("See more discussions", onMore)
    }
}

@Composable
fun FaqBox(list: List<Faq>) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    Column {
        SectionTitle("People also ask")
        list.take(5).forEachIndexed { i, f ->
            var open by remember { mutableStateOf(false) }
            if (i > 0) ThinDivider()
            Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.question, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = c.textSecondary)
                }
                if (open) {
                    Spacer(Modifier.height(8.dp))
                    Text(f.answer, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    if (f.url.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(f.title.ifBlank { T.host(f.url) }, color = c.link, fontSize = 14.sp, modifier = Modifier.clickable { Nav.openUrl(ctx, f.url) })
                    }
                }
            }
        }
    }
}

@Composable
fun TopStories(list: List<Article>, onMore: () -> Unit) {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    Column {
        SectionTitle("Top stories")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list) { a ->
                val src = NewsRepo.source(a.sourceId)
                Column(
                    Modifier.width(260.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2)
                        .clickable { Nav.openUrl(ctx, a.url) }
                ) {
                    NetImage(a.image, Modifier.fillMaxWidth().height(130.dp))
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Favicon(src?.site ?: a.url, null, 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(src?.name ?: "", color = c.text, fontSize = 12.sp, maxLines = 1)
                        }
                        src?.label?.let { lbl -> Spacer(Modifier.height(4.dp)); Label(lbl, c.yellow) }
                        Spacer(Modifier.height(6.dp))
                        Text(a.title, color = c.text, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(T.ago(a.time), color = c.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        MoreRow("More news", onMore)
    }
}
