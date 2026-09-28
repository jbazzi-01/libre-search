package org.libre.search.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.libre.search.core.Http
import org.libre.search.core.Prefs
import org.libre.search.data.LibraryStore
import org.libre.search.player.PlayerActivity
import org.libre.search.player.VideoLinks
import java.io.ByteArrayInputStream

class BrowserTab(val id: Long, val isPrivate: Boolean) {
    var webView: WebView? = null
    val title = mutableStateOf("")
    val url = mutableStateOf("")
    val progress = mutableIntStateOf(100)
    val canGoBack = mutableStateOf(false)
    val canGoForward = mutableStateOf(false)
    val favicon = mutableStateOf<Bitmap?>(null)
    val blocked = mutableIntStateOf(0)
    val desktop = mutableStateOf(false)
    val findCount = mutableStateOf("")
    /** Set when the user chose "open in page" for a video link, so it isn't redirected again. */
    var allowVideoPage: String? = null
}

object Tabs {
    val tabs = mutableStateListOf<BrowserTab>()
    val current = mutableStateOf<BrowserTab?>(null)
    val fullscreenView = mutableStateOf<View?>(null)
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var nextId = 1L

    private var wrapper: MutableContextWrapper? = null
    var fileChooser: ((Intent, ValueCallback<Array<Uri>>) -> Unit)? = null

    const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun attach(activity: Activity) {
        val w = wrapper
        if (w == null) wrapper = MutableContextWrapper(activity) else w.baseContext = activity
    }

    private fun ctx(): Context = wrapper ?: error("Browser not attached")

    fun open(url: String, private: Boolean = false, background: Boolean = false): BrowserTab {
        val tab = BrowserTab(nextId++, private)
        tab.webView = createWebView(tab)
        tab.url.value = url
        tabs.add(tab)
        if (!background || current.value == null) current.value = tab
        if (url.isNotBlank()) tab.webView?.loadUrl(url)
        return tab
    }

    /** Opens in the current tab if there is one, otherwise a new tab. */
    fun load(url: String, newTab: Boolean = true) {
        val cur = current.value
        if (newTab || cur == null) open(url) else cur.webView?.loadUrl(url)
    }

    fun select(tab: BrowserTab) { current.value = tab }

    fun close(tab: BrowserTab) {
        val idx = tabs.indexOf(tab)
        tabs.remove(tab)
        tab.webView?.apply {
            stopLoading()
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        tab.webView = null
        if (current.value == tab) current.value = tabs.getOrNull((idx - 1).coerceAtLeast(0)) ?: tabs.firstOrNull()
        if (tab.isPrivate && tabs.none { it.isPrivate }) {
            // Last private tab closed: wipe the cache it may have left behind.
            WebView(ctx()).apply { clearCache(true); destroy() }
        }
    }

    fun closeAll() { tabs.toList().forEach { close(it) } }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenView.value = null
    }

    fun setDesktop(tab: BrowserTab, on: Boolean) {
        tab.desktop.value = on
        tab.webView?.settings?.apply {
            userAgentString = if (on) DESKTOP_UA else null
            useWideViewPort = true
            loadWithOverviewMode = on
        }
        tab.webView?.reload()
    }

    private fun injectCss(view: WebView) {
        if (!Prefs.adBlock && !Prefs.cookieBanners) return
        val css = AdBlocker.cosmeticCss
        if (css.isBlank()) return
        val escaped = org.json.JSONObject.quote(css)
        view.evaluateJavascript(
            "(function(){try{if(document.getElementById('__libre_css'))return;var s=document.createElement('style');" +
                "s.id='__libre_css';s.textContent=$escaped;(document.head||document.documentElement).appendChild(s);}catch(e){}})();",
            null
        )
    }

    private fun openExternal(url: String): Boolean {
        val c = ctx()
        return try {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            if (url.startsWith("intent:")) {
                val fallback = try { Intent.parseUri(url, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") } catch (_: Exception) { null }
                if (fallback != null) current.value?.webView?.loadUrl(fallback)
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun maybeOpenPlayer(tab: BrowserTab, url: String): Boolean {
        if (!Prefs.videoPlayer || tab.allowVideoPage == url) return false
        if (!VideoLinks.isStream(url)) return false
        PlayerActivity.start(ctx(), url)
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView {
        val wv = WebView(ctx())
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, false)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = !tab.isPrivate
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = false // no URL checks sent to Google
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host
                if (!request.isForMainFrame && AdBlocker.isBlocked(host)) {
                    view.post { tab.blocked.intValue++ }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme?.lowercase() ?: ""
                if (scheme != "http" && scheme != "https") {
                    if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return false
                    return openExternal(url)
                }
                if (request.isForMainFrame && maybeOpenPlayer(tab, url)) return true
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.url.value = url
                tab.favicon.value = favicon
                tab.blocked.intValue = 0
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
            }

            override fun onPageCommitVisible(view: WebView, url: String) { injectCss(view) }

            override fun onPageFinished(view: WebView, url: String) {
                injectCss(view)
                tab.url.value = url
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
                if (!tab.isPrivate) LibraryStore.addPage(view.title ?: "", url)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                tab.url.value = url
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
                // Sites like YouTube change page without a real navigation.
                if (!isReload && Prefs.videoPlayer && tab.allowVideoPage != url && VideoLinks.isStream(url)) {
                    view.stopLoading()
                    tab.allowVideoPage = url
                    if (view.canGoBack()) view.goBack()
                    PlayerActivity.start(ctx(), url)
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress.intValue = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) { tab.title.value = title ?: "" }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { tab.favicon.value = icon }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenCallback = callback
                fullscreenView.value = view
            }

            override fun onHideCustomView() {
                fullscreenCallback = null
                fullscreenView.value = null
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false // blocks pop-ups opened by scripts
                val newTab = open("", tab.isPrivate)
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = newTab.webView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                tabs.firstOrNull { it.webView == window }?.let { close(it) }
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val launcher = fileChooser ?: return false
                return try {
                    launcher(fileChooserParams.createIntent(), filePathCallback)
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false) // privacy: sites never get your location
            }

            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }
        wv.setFindListener { active, count, done ->
            if (done) tab.findCount.value = if (count == 0) "0/0" else "${active + 1}/$count"
        }
        return wv
    }

    fun download(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, fileName: String? = null) {
        val c = ctx()
        try {
            val name = fileName ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
            val req = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent ?: Http.BROWSER_UA)
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(c, "Downloading $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(c, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
