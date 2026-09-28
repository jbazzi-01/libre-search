package org.libre.search.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.libre.search.core.Http
import org.libre.search.core.Prefs
import org.libre.search.data.LibraryStore
import org.libre.search.search.Brave
import org.libre.search.ui.Nav
import org.libre.search.ui.theme.LocalLibre

private suspend fun remoteSuggestions(q: String): List<String> {
    Brave.suggest(q)?.let { if (it.isNotEmpty()) return it }
    // Fallback: article titles from Wikipedia's open search.
    return try {
        val lang = if (Prefs.panelLang == "en") "en" else "fr"
        val body = Http.getString(
            Http.url("https://$lang.wikipedia.org/w/api.php", mapOf("action" to "opensearch", "search" to q, "limit" to "8", "namespace" to "0", "format" to "json"))
        )
        val arr = org.json.JSONArray(body).optJSONArray(1) ?: return emptyList()
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun SearchInputScreen(initial: String, tab: String, inTab: Boolean = false) {
    val c = LocalLibre.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var remote by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) Nav.search(text, tab)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(value.text) {
        val q = value.text.trim()
        if (q.length < 2) { remote = emptyList(); return@LaunchedEffect }
        delay(250)
        remote = remoteSuggestions(q)
    }

    val history = LibraryStore.searchSuggestions(value.text, if (value.text.isBlank()) 12 else 3)
    val suggestions = remote.filter { r -> history.none { it.equals(r, true) } }.take(8)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(50)).background(c.surface2).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text) }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 18.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { Nav.search(value.text, tab, inTab) }),
                modifier = Modifier.weight(1f).focusRequester(focus).padding(vertical = 14.dp),
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Box {
                        if (value.text.isEmpty()) Text("Search or type URL", color = c.textSecondary, fontSize = 18.sp)
                        inner()
                    }
                }
            )
            if (value.text.isNotEmpty()) {
                IconButton(onClick = { value = TextFieldValue("") }) { Icon(Icons.Default.Clear, null, tint = c.textSecondary) }
            } else {
                IconButton(onClick = {
                    try {
                        voice.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        )
                    } catch (_: Exception) {
                        Nav.toast.value = "No voice input app on this phone"
                    }
                }) { Icon(Icons.Default.Mic, null, tint = c.accent) }
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(history) { h ->
                SuggestionRow(Icons.Default.History, h, onFill = { value = TextFieldValue("$h ", TextRange(h.length + 1)) }) { Nav.search(h, tab) }
            }
            if (history.isNotEmpty() && suggestions.isNotEmpty()) item { HorizontalDivider(color = c.outline, modifier = Modifier.padding(vertical = 4.dp)) }
            items(suggestions) { s ->
                SuggestionRow(Icons.Default.Search, s, onFill = { value = TextFieldValue("$s ", TextRange(s.length + 1)) }) { Nav.search(s, tab) }
            }
            if (value.text.isBlank() && history.isEmpty()) {
                item {
                    Text(
                        "Your searches stay on this phone.", color = c.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onFill: () -> Unit, onClick: () -> Unit) {
    val c = LocalLibre.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1)
        IconButton(onClick = onFill) { Icon(Icons.Default.NorthWest, null, tint = c.textSecondary) }
    }
}
