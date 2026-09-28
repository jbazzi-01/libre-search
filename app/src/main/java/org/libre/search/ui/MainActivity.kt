package org.libre.search.ui

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.libre.search.browser.AdBlocker
import org.libre.search.browser.BrowserScreen
import org.libre.search.browser.TabSwitcherScreen
import org.libre.search.browser.Tabs
import org.libre.search.core.Prefs
import org.libre.search.ui.home.HomeScreen
import org.libre.search.ui.library.LibraryScreen
import org.libre.search.ui.news.NewsScreen
import org.libre.search.ui.search.ResultsScreen
import org.libre.search.ui.search.SearchInputScreen
import org.libre.search.ui.settings.SettingsScreen
import org.libre.search.ui.theme.LibreTheme
import org.libre.search.ui.theme.LocalLibre

class MainActivity : ComponentActivity() {

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Tabs.attach(this)

        fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
            fileCallback = null
        }
        Tabs.fileChooser = { intent, cb ->
            fileCallback?.onReceiveValue(null)
            fileCallback = cb
            fileLauncher.launch(intent)
        }
        locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

        val sp = getSharedPreferences("libre_prefs", MODE_PRIVATE)
        if (Prefs.locationMode == "gps" && !sp.getBoolean("asked_location", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            sp.edit().putBoolean("asked_location", true).apply()
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }

        if (AdBlocker.needsUpdate()) {
            lifecycleScope.launch { try { AdBlocker.update() } catch (_: Exception) {} }
        }

        handleIntent(intent)
        setContent { LibreTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        Tabs.attach(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { Nav.search(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
                val url = Regex("https?://\\S+").find(text)?.value
                if (url != null) Nav.openUrl(this, url) else Nav.search(text)
            }
            Intent.ACTION_ASSIST -> Nav.push(Screen.SearchInput(""))
            ACTION_OPEN_URL -> intent.getStringExtra(EXTRA_URL)?.let { url ->
                Nav.openUrl(this, url, forceBrowser = intent.getBooleanExtra(EXTRA_ALLOW_VIDEO, false))
            }
        }
        intent.action = null
    }

    @Composable
    private fun App() {
        val c = LocalLibre.current
        val holder = rememberSaveableStateHolder()
        val screen = Nav.current
        val fullscreen = Tabs.fullscreenView.value

        val toast = Nav.toast.value
        LaunchedEffect(toast) {
            if (toast != null) {
                Toast.makeText(this@MainActivity, toast, Toast.LENGTH_SHORT).show()
                Nav.toast.value = null
            }
        }

        BackHandler(enabled = fullscreen != null) { Tabs.exitFullscreen() }
        BackHandler(enabled = fullscreen == null && Nav.stack.size > 1 && screen != Screen.Browser) { Nav.pop() }

        LaunchedEffect(fullscreen) {
            val ctl = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen != null) {
                ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ctl.show(WindowInsetsCompat.Type.systemBars())
            }
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !c.isDark
        }
        LaunchedEffect(c.isDark) {
            val ctl = WindowCompat.getInsetsController(window, window.decorView)
            ctl.isAppearanceLightStatusBars = !c.isDark
            ctl.isAppearanceLightNavigationBars = !c.isDark
        }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            val showBar = screen == Screen.Home || screen == Screen.News || screen == Screen.Library
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    holder.SaveableStateProvider(System.identityHashCode(screen)) {
                        when (val s = screen) {
                            Screen.Home -> HomeScreen()
                            is Screen.SearchInput -> SearchInputScreen(s.initial, s.tab, s.inTab)
                            is Screen.Results -> ResultsScreen(s.state)
                            Screen.Browser -> BrowserScreen()
                            Screen.TabSwitcher -> TabSwitcherScreen()
                            Screen.News -> NewsScreen()
                            Screen.Library -> LibraryScreen()
                            Screen.Settings -> SettingsScreen()
                        }
                    }
                }
                if (showBar) BottomBar(screen)
            }
            if (fullscreen != null) {
                AndroidView(
                    factory = { _ ->
                        (fullscreen.parent as? ViewGroup)?.removeView(fullscreen)
                        fullscreen
                    },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            }
        }
    }

    @Composable
    private fun BottomBar(screen: Screen) {
        val c = LocalLibre.current
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = c.text, selectedTextColor = c.text,
            unselectedIconColor = c.textSecondary, unselectedTextColor = c.textSecondary,
            indicatorColor = c.chip,
        )
        NavigationBar(containerColor = c.bg) {
            NavigationBarItem(
                selected = screen == Screen.Home, onClick = { Nav.root(Screen.Home) },
                icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }, colors = colors
            )
            NavigationBarItem(
                selected = screen == Screen.News, onClick = { Nav.root(Screen.News) },
                icon = { Icon(Icons.Default.Newspaper, null) }, label = { Text("News") }, colors = colors
            )
            NavigationBarItem(
                selected = false,
                onClick = {
                    if (Tabs.tabs.isNotEmpty()) Nav.push(Screen.TabSwitcher)
                    else Nav.push(Screen.SearchInput(""))
                },
                icon = { Icon(Icons.Default.Tab, null) }, label = { Text("Tabs (${Tabs.tabs.size})") }, colors = colors
            )
            NavigationBarItem(
                selected = screen == Screen.Library, onClick = { Nav.root(Screen.Library) },
                icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, null) }, label = { Text("Library") }, colors = colors
            )
        }
    }

    companion object {
        const val ACTION_OPEN_URL = "org.libre.search.OPEN_URL"
        const val EXTRA_URL = "url"
        const val EXTRA_ALLOW_VIDEO = "allow_video"
    }
}
