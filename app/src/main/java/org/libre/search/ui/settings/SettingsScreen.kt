package org.libre.search.ui.settings

import android.Manifest
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.libre.search.browser.AdBlocker
import org.libre.search.core.LocationHelper
import org.libre.search.core.Prefs
import org.libre.search.data.LibraryStore
import org.libre.search.news.NewsRepo
import org.libre.search.news.Sources
import org.libre.search.news.Tendency
import org.libre.search.ui.Label
import org.libre.search.ui.Nav
import org.libre.search.ui.theme.LocalLibre
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Section(title: String) {
    val c = LocalLibre.current
    Text(title, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLibre.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.textSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(title: String, current: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val c = LocalLibre.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            Text(options.firstOrNull { it.first == current }?.second ?: current, color = c.textSecondary, fontSize = 13.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(id) })
            }
        }
    }
}

@Composable
private fun Action(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val c = LocalLibre.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, color = c.text, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = c.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun KeyField(label: String, value: String, help: String, onSave: (String) -> Unit) {
    val c = LocalLibre.current
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        OutlinedTextField(
            text, { text = it }, label = { Text(label) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(help, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSave(text) }, enabled = text != value) { Text("Save") }
        }
    }
}

@Composable
fun SettingsScreen() {
    val c = LocalLibre.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val v = Prefs.version.intValue
    var cityDialog by remember { mutableStateOf(false) }
    var addSource by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text) }
            Text("Settings", color = c.text, fontSize = 22.sp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Section("API keys (stored only on this phone)")
            KeyField("Brave Search API key", Prefs.braveKey, "Free plan at api-dashboard.search.brave.com") {
                Prefs.braveKey = it; Prefs.suggestUnavailable = false
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }
            KeyField("TMDB API key or read token", Prefs.tmdbKey, "Free at themoviedb.org → Settings → API") {
                Prefs.tmdbKey = it
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }

            Section("Search")
            Choice("Region", Prefs.country, listOf(
                "FR" to "France", "BE" to "Belgium", "CH" to "Switzerland", "CA" to "Canada", "GB" to "United Kingdom",
                "US" to "United States", "ES" to "Spain", "DE" to "Germany", "IT" to "Italy", "ALL" to "Worldwide"
            )) { Prefs.country = it }
            Choice("Results language", Prefs.searchLang, listOf("fr" to "French", "en" to "English", "all" to "Any language")) { Prefs.searchLang = it }
            Choice("Panels language (Wikipedia, movies)", Prefs.panelLang, listOf("fr" to "French", "en" to "English")) { Prefs.panelLang = it }
            Choice("SafeSearch", Prefs.safeSearch, listOf("off" to "Off", "moderate" to "Moderate", "strict" to "Strict")) { Prefs.safeSearch = it }
            Toggle("Hide AI-generated images", "Filters known AI image sites and labels", Prefs.hideAiImages) { Prefs.hideAiImages = it }
            Toggle("Hide Israeli and pro-Israel outlets", "Applies to web results, videos and forums", Prefs.hideIsraeliOutlets) { Prefs.hideIsraeliOutlets = it }
            Toggle("Save search and browsing history", "Kept only on this phone", Prefs.saveHistory) { Prefs.saveHistory = it }
            val blocked = Prefs.blockedDomains.sorted()
            if (blocked.isNotEmpty()) {
                Text("Sites you blocked", color = c.text, fontSize = 16.sp, modifier = Modifier.padding(start = 20.dp, top = 10.dp))
                blocked.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d, color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { Prefs.blockedDomains = Prefs.blockedDomains - d }) {
                            Icon(Icons.Default.Close, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Section("Location")
            Choice("Location for weather and places", Prefs.locationMode, listOf("gps" to "Phone location (rounded to ~1 km)", "manual" to "A city I choose")) {
                Prefs.locationMode = it
                if (it == "gps") locPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                else cityDialog = true
            }
            if (Prefs.locationMode == "manual") Action("City", Prefs.manualCity.ifBlank { "Not set" }) { cityDialog = true }

            Section("Browser")
            Toggle("Open links in the built-in browser", "Otherwise your default browser opens them", Prefs.builtInBrowser) { Prefs.builtInBrowser = it }
            Toggle("Block ads and trackers", "${AdBlocker.blockedCount()} domains in the block list", Prefs.adBlock) { Prefs.adBlock = it }
            Toggle("Hide cookie banners", null, Prefs.cookieBanners) { Prefs.cookieBanners = it }
            val updated = if (Prefs.adListsUpdated == 0L) "Never (uses a small built-in list)" else "Last update: " +
                SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(Prefs.adListsUpdated))
            Action(if (updating) "Updating filter lists…" else "Update filter lists now", "EasyList, EasyPrivacy, Liste FR, Fanboy, StevenBlack. $updated") {
                if (!updating) {
                    updating = true
                    scope.launch {
                        val n = try { AdBlocker.update() } catch (_: Exception) { -1 }
                        updating = false
                        Toast.makeText(ctx, if (n >= 0) "$n domains blocked" else "Update failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Action("Clear cookies and site data", "Signs you out of websites in the built-in browser") {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                Toast.makeText(ctx, "Cookies and site data cleared", Toast.LENGTH_SHORT).show()
            }
            Action("Clear history", "${LibraryStore.history.size} entries") { LibraryStore.clearHistory() }

            Section("Videos")
            Toggle(
                "Play videos in the built-in player",
                "YouTube, PeerTube, SoundCloud and Bandcamp, without ads", Prefs.videoPlayer
            ) { Prefs.videoPlayer = it }

            Section("News sources")
            Text(
                "Switch sources on or off, or add your own. Feeds are fetched directly from each outlet.",
                color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp)
            )
            Tendency.entries.forEach { t ->
                val list = Sources.all().filter { it.tendency == t }
                if (list.isNotEmpty()) {
                    Text(t.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
                    list.forEach { src ->
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(src.name, color = c.text, fontSize = 15.sp)
                                    src.label?.let { Spacer(Modifier.width(6.dp)); Label(it, c.yellow) }
                                }
                                Text(
                                    (if (src.lang == "fr") "French" else "English") + if (src.id in NewsRepo.failing) " · couldn't reach last time" else "",
                                    color = c.textSecondary, fontSize = 12.sp
                                )
                            }
                            if (src.custom) {
                                IconButton(onClick = { Sources.removeCustom(src.id) }) { Icon(Icons.Default.Close, null, tint = c.textSecondary) }
                            }
                            Switch(checked = src.id !in Prefs.disabledFeeds, onCheckedChange = { Sources.setEnabled(src.id, it) })
                        }
                    }
                }
            }
            Action("+ Add a source", "Paste a website or RSS feed address") { addSource = true }

            Section("Appearance")
            Choice("Theme", Prefs.theme, listOf("dark" to "Dark", "light" to "Light", "system" to "Follow system")) { Prefs.theme = it }

            Section("Privacy")
            Text(
                "No account, no tracking, no analytics. Your history, bookmarks and keys stay on this phone.\n\n" +
                    "What leaves the phone, and where:\n" +
                    "• Search words → Brave Search API (web, images, videos)\n" +
                    "• Search words → TMDB and Wikipedia/Wikidata (panels)\n" +
                    "• Rounded location (~1 km) → Open-Meteo (weather) and OpenStreetMap (places, map tiles)\n" +
                    "• News → fetched directly from each outlet's RSS feed\n" +
                    "• Videos → fetched directly from the video site, without its player or ads\n\n" +
                    "A VPN hides your IP address from all of these.",
                color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (cityDialog) {
        var city by remember { mutableStateOf(Prefs.manualCity) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cityDialog = false },
            title = { Text("Your city") },
            text = { OutlinedTextField(city, { city = it }, singleLine = true, placeholder = { Text("e.g. Champigny-sur-Marne") }) },
            confirmButton = {
                TextButton(enabled = !busy && city.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        val p = LocationHelper.geocode(city.trim())
                        busy = false
                        if (p == null) Toast.makeText(ctx, "City not found", Toast.LENGTH_SHORT).show()
                        else {
                            Prefs.manualCity = p.name; Prefs.manualLat = p.lat; Prefs.manualLon = p.lon
                            Prefs.locationMode = "manual"
                            cityDialog = false
                        }
                    }
                }) { Text(if (busy) "…" else "Save") }
            },
            dismissButton = { TextButton(onClick = { cityDialog = false }) { Text("Cancel") } }
        )
    }

    if (addSource) {
        var url by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var lang by remember { mutableStateOf("fr") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addSource = false },
            title = { Text("Add a news source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(url, { url = it }, label = { Text("Website or feed address") }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("fr" to "French", "en" to "English").forEach { (id, label) ->
                            Text(
                                label, color = if (lang == id) c.bg else c.text,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (lang == id) c.accent else c.chip)
                                    .clickable { lang = id }.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Text(
                        "It should follow the same rules as your other sources: no big business, no state funding, no pro-government, pro-Israel, pro-China or pro-Russia outlets.",
                        color = c.textSecondary, fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && url.isNotBlank(), onClick = click@{
                    Sources.rejection(url.trim())?.let { reason ->
                        Toast.makeText(ctx, "Not added: $reason", Toast.LENGTH_LONG).show()
                        return@click
                    }
                    busy = true
                    scope.launch {
                        val found = NewsRepo.probe(url.trim())
                        busy = false
                        if (found == null) Toast.makeText(ctx, "No RSS feed found at that address", Toast.LENGTH_LONG).show()
                        else {
                            val site = if (url.startsWith("http")) url.trim() else "https://" + url.trim()
                            Sources.addCustom(name.ifBlank { found.second }, site, found.first, lang)
                            addSource = false
                            Toast.makeText(ctx, "Added ${name.ifBlank { found.second }}", Toast.LENGTH_SHORT).show()
                            scope.launch { try { NewsRepo.refresh() } catch (_: Exception) {} }
                        }
                    }
                }) { Text(if (busy) "Checking…" else "Add") }
            },
            dismissButton = { TextButton(onClick = { addSource = false }) { Text("Cancel") } }
        )
    }
}
