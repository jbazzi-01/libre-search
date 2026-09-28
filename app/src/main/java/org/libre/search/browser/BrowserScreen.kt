package org.libre.search.browser

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.libre.search.core.Text as T
import org.libre.search.data.LibraryStore
import org.libre.search.ui.Favicon
import org.libre.search.ui.Nav
import org.libre.search.ui.Screen
import org.libre.search.ui.search.copyLink
import org.libre.search.ui.search.shareLink
import org.libre.search.ui.theme.LocalLibre

private val PrivatePurple = Color(0xFF3B2A5C)

@Composable
fun BrowserScreen() {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val tab = Tabs.current.value
    if (tab == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { Nav.pop() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }

    BackHandler {
        when {
            finding -> { finding = false; tab.webView?.clearMatches() }
            tab.webView?.canGoBack() == true -> tab.webView?.goBack()
            else -> Nav.pop()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(if (tab.isPrivate) PrivatePurple else c.bg).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.Default.Home, null, tint = c.text) }
            Row(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50)).background(c.surface2)
                    .clickable { Nav.push(Screen.SearchInput(tab.url.value, inTab = true)) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isPrivate) Icon(Icons.Default.VisibilityOff, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                else if (tab.url.value.startsWith("https")) Icon(Icons.Default.Lock, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    T.host(tab.url.value).ifBlank { tab.url.value }, color = c.text, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (tab.blocked.intValue > 0) {
                    Icon(Icons.Default.Shield, null, tint = c.green, modifier = Modifier.size(16.dp))
                    Text(" ${tab.blocked.intValue}", color = c.green, fontSize = 12.sp)
                }
            }
            Box(
                Modifier.padding(horizontal = 8.dp).size(26.dp).border(2.dp, c.text, RoundedCornerShape(6.dp))
                    .clickable { Nav.push(Screen.TabSwitcher) },
                contentAlignment = Alignment.Center
            ) { Text("${Tabs.tabs.size}", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = c.text) }
                BrowserMenu(menu, tab, onDismiss = { menu = false }, onFind = { finding = true })
            }
        }
        if (tab.progress.intValue < 100) {
            LinearProgressIndicator(
                progress = { tab.progress.intValue / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = c.accent, trackColor = Color.Transparent
            )
        }
        if (finding) {
            Row(
                Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    findText, { findText = it; tab.webView?.findAllAsync(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { tab.webView?.findNext(true) }),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner -> Box { if (findText.isEmpty()) Text("Find in page", color = c.textSecondary); inner() } }
                )
                Text(tab.findCount.value, color = c.textSecondary, fontSize = 13.sp)
                IconButton(onClick = { tab.webView?.findNext(false) }) { Icon(Icons.Default.KeyboardArrowUp, null, tint = c.text) }
                IconButton(onClick = { tab.webView?.findNext(true) }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = c.text) }
                IconButton(onClick = { finding = false; findText = ""; tab.webView?.clearMatches() }) { Icon(Icons.Default.Close, null, tint = c.text) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(tab.id) {
                AndroidView(
                    factory = { _ ->
                        val wv = tab.webView!!
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        wv
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { tab.webView?.goBack() }, enabled = tab.canGoBack.value) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (tab.canGoBack.value) c.text else c.outline)
            }
            IconButton(onClick = { tab.webView?.goForward() }, enabled = tab.canGoForward.value) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (tab.canGoForward.value) c.text else c.outline)
            }
            IconButton(onClick = { Nav.push(Screen.SearchInput("")) }) { Icon(Icons.Default.Add, null, tint = c.text) }
            IconButton(onClick = { shareLink(ctx, tab.url.value, tab.title.value) }) {
                Icon(Icons.Default.Share, null, tint = c.text)
            }
            IconButton(onClick = { LibraryStore.toggleBookmark(tab.title.value, tab.url.value) }) {
                val saved = LibraryStore.bookmarks.any { it.url == tab.url.value }
                Icon(
                    if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    null, tint = if (saved) c.accent else c.text
                )
            }
        }
    }
}

@Composable
private fun BrowserMenu(expanded: Boolean, tab: BrowserTab, onDismiss: () -> Unit, onFind: () -> Unit) {
    val ctx = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Reload") }, onClick = { onDismiss(); tab.webView?.reload() })
        DropdownMenuItem(text = { Text("New tab") }, onClick = { onDismiss(); Nav.push(Screen.SearchInput("")) })
        DropdownMenuItem(text = { Text("New private tab") }, onClick = { onDismiss(); Tabs.open("about:blank", private = true); Nav.push(Screen.SearchInput("")) })
        DropdownMenuItem(text = { Text("Find in page") }, onClick = { onDismiss(); onFind() })
        DropdownMenuItem(
            text = { Text(if (tab.desktop.value) "✓ Desktop site" else "Desktop site") },
            onClick = { onDismiss(); Tabs.setDesktop(tab, !tab.desktop.value) }
        )
        DropdownMenuItem(text = { Text("Copy link") }, onClick = { onDismiss(); copyLink(ctx, tab.url.value) })
        DropdownMenuItem(text = { Text("Open in another browser") }, onClick = {
            onDismiss()
            try {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url.value)), "Open with"))
            } catch (_: Exception) {}
        })
        DropdownMenuItem(text = { Text("Bookmarks & history") }, onClick = { onDismiss(); Nav.push(Screen.Library) })
        DropdownMenuItem(text = { Text("Downloads") }, onClick = {
            onDismiss()
            try { ctx.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) {}
        })
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("${tab.blocked.intValue} ads & trackers blocked on this page", fontSize = 13.sp) },
            onClick = onDismiss
        )
    }
}

@Composable
fun TabSwitcherScreen() {
    val c = LocalLibre.current
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text) }
            Text("${Tabs.tabs.size} tabs", color = c.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text(
                "Close all", color = c.link, fontSize = 15.sp,
                modifier = Modifier.clickable { Tabs.closeAll(); Nav.root(Screen.Home) }.padding(12.dp)
            )
        }
        if (Tabs.tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open tabs", color = c.textSecondary)
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(Tabs.tabs, key = { it.id }) { t ->
                val sel = t == Tabs.current.value
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (t.isPrivate) PrivatePurple else c.surface)
                        .border(if (sel) 2.dp else 0.dp, if (sel) c.accent else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable {
                            Tabs.select(t)
                            if (Nav.stack.contains(Screen.Browser)) Nav.pop() else Nav.replaceTop(Screen.Browser)
                        }
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = t.favicon.value
                        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(16.dp))
                        else Favicon(t.url.value, null, 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.title.value.ifBlank { T.host(t.url.value) }, color = c.text, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { Tabs.close(t) }) { Icon(Icons.Default.Close, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.8f).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Favicon(t.url.value, null, 40.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(T.host(t.url.value), color = c.textSecondary, fontSize = 12.sp, maxLines = 1)
                            if (t.isPrivate) Text("Private", color = c.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "+ New tab", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(c.chip)
                    .clickable { Nav.push(Screen.SearchInput("")) }.padding(14.dp)
            )
            Text(
                "+ Private tab", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(PrivatePurple)
                    .clickable { Tabs.open("about:blank", private = true); Nav.push(Screen.SearchInput("")) }.padding(14.dp)
            )
        }
    }
}
