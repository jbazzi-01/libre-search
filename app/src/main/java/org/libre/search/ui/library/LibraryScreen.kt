package org.libre.search.ui.library

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.libre.search.core.Text as T
import org.libre.search.data.Bookmark
import org.libre.search.data.LibraryStore
import org.libre.search.ui.Favicon
import org.libre.search.ui.Nav
import org.libre.search.ui.theme.LocalLibre
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen() {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("history") }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Text("Library", color = c.text, fontSize = 26.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("history" to "History", "bookmarks" to "Bookmarks", "downloads" to "Downloads")) { (id, label) ->
                val on = id == tab
                Text(
                    label, color = if (on) (if (c.isDark) Color(0xFF062E6F) else Color.White) else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accent else c.chip)
                        .clickable {
                            if (id == "downloads") {
                                try { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) {}
                            } else tab = id
                        }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        when (tab) {
            "history" -> {
                if (LibraryStore.history.isEmpty()) {
                    Text("No history yet.", color = c.textSecondary, modifier = Modifier.padding(24.dp))
                } else {
                    Text(
                        "Clear all history", color = c.link, fontSize = 14.sp,
                        modifier = Modifier.clickable { confirmClear = true }.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(LibraryStore.history.take(500)) { h ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (h.kind == "search") Nav.search(h.title) else Nav.openUrl(ctx, h.url)
                                }.padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (h.kind == "search") Icon(Icons.Default.History, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                                else Favicon(h.url, null, 22.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (h.kind == "page") T.host(h.url) + " · " else "") + fmt.format(Date(h.time)),
                                        color = c.textSecondary, fontSize = 12.sp, maxLines = 1
                                    )
                                }
                                IconButton(onClick = { LibraryStore.removeHistory(h) }) {
                                    Icon(Icons.Default.Close, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            "bookmarks" -> {
                if (LibraryStore.bookmarks.isEmpty()) {
                    Text("No bookmarks yet. Tap the bookmark icon in the browser to save a page.", color = c.textSecondary, modifier = Modifier.padding(24.dp))
                } else {
                    val grouped = LibraryStore.bookmarks.groupBy { it.folder }
                    LazyColumn(Modifier.fillMaxSize()) {
                        grouped.toSortedMap().forEach { (folder, list) ->
                            item {
                                Text(
                                    folder.ifBlank { "Bookmarks" }, color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                                )
                            }
                            items(list) { b ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { Nav.openUrl(ctx, b.url) }.padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Favicon(b.url, null, 24.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(b.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(T.host(b.url), color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
                                    }
                                    IconButton(onClick = { editing = b }) { Icon(Icons.Default.Edit, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
                                    IconButton(onClick = { LibraryStore.removeBookmark(b) }) { Icon(Icons.Default.Close, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { b ->
        var title by remember(b) { mutableStateOf(b.title) }
        var folder by remember(b) { mutableStateOf(b.folder) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit bookmark") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(folder, { folder = it }, label = { Text("Folder") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { LibraryStore.updateBookmark(b, b.copy(title = title, folder = folder.trim())); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Searches and visited pages will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { LibraryStore.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
