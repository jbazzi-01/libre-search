@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.libre.search.player

import android.Manifest
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libre.search.ui.MainActivity
import org.libre.search.ui.theme.LibreTheme
import org.libre.search.ui.theme.LocalLibre
import org.schabi.newpipe.extractor.stream.StreamInfo

class PlayerActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val info = mutableStateOf<StreamInfo?>(null)
    private val error = mutableStateOf<String?>(null)
    private val qualities = mutableStateOf<List<Quality>>(emptyList())
    private val selected = mutableStateOf<Quality?>(null)
    private val inPip = mutableStateOf(false)
    private val background = mutableStateOf(false)
    private val speed = mutableStateOf(1f)
    private var pageUrl: String = ""
    private var playerView: PlayerView? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(e: PlaybackException) {
            // A different quality often works when one stream is refused.
            val list = qualities.value
            val cur = selected.value
            val next = list.dropWhile { it != cur }.drop(1).firstOrNull()
            if (next != null && info.value != null) {
                Toast.makeText(this@PlayerActivity, "Trying ${next.label}…", Toast.LENGTH_SHORT).show()
                play(next, keepPosition = true)
            } else {
                error.value = "Couldn't play this video (${e.errorCodeName})."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        PlayerHolder.get(this).addListener(listener)
        handle(intent)
        setContent { LibreTheme { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return
        if (url == pageUrl && info.value != null) return
        pageUrl = url
        info.value = null
        error.value = null
        qualities.value = emptyList()
        lifecycleScope.launch {
            try {
                val i = withContext(Dispatchers.IO) { StreamInfo.getInfo(url) }
                info.value = i
                val q = StreamSources.qualities(i)
                qualities.value = q
                val audioOnly = i.videoStreams.isEmpty() && i.videoOnlyStreams.isEmpty()
                play(StreamSources.defaultQuality(q, audioOnly), keepPosition = false)
            } catch (e: Throwable) {
                error.value = "This video couldn't be loaded here (${e.javaClass.simpleName})."
            }
        }
    }

    private fun play(q: Quality?, keepPosition: Boolean) {
        val i = info.value ?: return
        val p = PlayerHolder.get(this)
        val pos = if (keepPosition) p.currentPosition else 0L
        try {
            val src = StreamSources.build(this, i, q)
            selected.value = q
            p.setMediaSource(src, pos)
            p.prepare()
            p.playWhenReady = true
            p.setPlaybackSpeed(speed.value)
        } catch (e: Throwable) {
            error.value = "No playable stream (${e.message})."
        }
    }

    private fun openPage() {
        val i = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_URL)
            .putExtra(MainActivity.EXTRA_URL, pageUrl)
            .putExtra(MainActivity.EXTRA_ALLOW_VIDEO, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        PlayerHolder.get(this).pause()
        startActivity(i)
        finish()
    }

    private fun download() {
        val i = info.value ?: return
        val muxed = i.videoStreams.filter { it.isUrl }.maxByOrNull { it.height }
        val stream = muxed ?: StreamSources.bestAudio(i)?.takeIf { it.isUrl }
        if (stream == null) {
            Toast.makeText(this, "No downloadable file for this video", Toast.LENGTH_LONG).show(); return
        }
        val ext = stream.format?.suffix ?: "mp4"
        val name = i.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80) + "." + ext
        try {
            val req = DownloadManager.Request(Uri.parse(stream.content))
                .addRequestHeader("User-Agent", NpDownloader.UA)
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Downloading $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, pageUrl)
        startActivity(Intent.createChooser(send, "Share"))
    }

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = PlayerHolder.get(this)
        if (!background.value && p.isPlaying && selected.value?.video != null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        playerView?.useController = !isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Leaving without PiP or background mode pauses, like a normal video page.
        if (!background.value && !isInPictureInPictureMode && !isChangingConfigurations) {
            // keep playing audio only if the user chose it
            if (selected.value?.video != null) PlayerHolder.get(this).pause()
        }
    }

    override fun onDestroy() {
        PlayerHolder.get(this).removeListener(listener)
        if (isFinishing && !background.value) PlayerHolder.get(this).stop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        playerView?.player = null
        super.onDestroy()
    }

    @Composable
    private fun Screen() {
        val c = LocalLibre.current
        val i = info.value
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        BackHandler { finish() }
        Column(
            Modifier
                .fillMaxSize()
                .background(if (inPip.value || landscape) Color.Black else c.bg)
        ) {
            if (!inPip.value && !landscape) Spacer(Modifier.statusBarsPadding())
            Box(
                (if (inPip.value || landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            player = PlayerHolder.get(ctx)
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setFullscreenButtonClickListener { full ->
                                requestedOrientation = if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                            playerView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (i == null && error.value == null) CircularProgressIndicator(color = Color.White)
            }
            if (inPip.value || landscape) return@Column

            val err = error.value
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (err != null) {
                    Text(err, color = c.red, fontSize = 15.sp)
                    TextButton(onClick = { openPage() }) { Text("Open the original page instead") }
                }
                if (i != null) {
                    Text(i.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                    Spacer(Modifier.height(6.dp))
                    val views = if (i.viewCount > 0) " · " + formatViews(i.viewCount) else ""
                    Text((i.uploaderName ?: "") + views, color = c.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var qOpen by remember { mutableStateOf(false) }
                        Box {
                            Chip(Icons.Default.HighQuality, selected.value?.label ?: "Quality") { qOpen = true }
                            DropdownMenu(expanded = qOpen, onDismissRequest = { qOpen = false }) {
                                qualities.value.forEach { q ->
                                    DropdownMenuItem(text = { Text(q.label + if (q == selected.value) "  ✓" else "") }, onClick = {
                                        qOpen = false; play(q, keepPosition = true)
                                    })
                                }
                            }
                        }
                        var sOpen by remember { mutableStateOf(false) }
                        Box {
                            Chip(Icons.Default.Speed, "${speed.value}x") { sOpen = true }
                            DropdownMenu(expanded = sOpen, onDismissRequest = { sOpen = false }) {
                                listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                                    DropdownMenuItem(text = { Text("${s}x") }, onClick = {
                                        sOpen = false; speed.value = s; PlayerHolder.get(this@PlayerActivity).setPlaybackSpeed(s)
                                    })
                                }
                            }
                        }
                        Chip(Icons.Default.Headphones, if (background.value) "Background: on" else "Background: off") {
                            background.value = !background.value
                            Toast.makeText(
                                this@PlayerActivity,
                                if (background.value) "Keeps playing when you leave the app" else "Picture-in-picture when you leave the app",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Chip(Icons.Default.PictureInPictureAlt, "Mini player") { enterPip() }
                        Chip(Icons.Default.Download, "Download") { download() }
                        Chip(Icons.Default.Share, "Share") { share() }
                        Chip(Icons.AutoMirrored.Filled.OpenInNew, "Page") { openPage() }
                    }
                    Spacer(Modifier.height(16.dp))
                    var expanded by remember { mutableStateOf(false) }
                    val desc = i.description?.content?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT).toString() } ?: ""
                    if (desc.isNotBlank()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
                                .clickable { expanded = !expanded }.padding(12.dp)
                        ) {
                            Text(
                                desc.trim(), color = c.text, fontSize = 14.sp, lineHeight = 20.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 4
                            )
                            Text(if (expanded) "Show less" else "…more", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
        val c = LocalLibre.current
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(c.chip).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = c.text, fontSize = 13.sp)
        }
    }

    private fun formatViews(n: Long): String = when {
        n >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fB views", n / 1e9)
        n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM views", n / 1e6)
        n >= 1_000 -> String.format(java.util.Locale.US, "%.0fK views", n / 1e3)
        else -> "$n views"
    }

    companion object {
        const val EXTRA_URL = "url"

        fun start(ctx: Context, url: String) {
            val i = Intent(ctx, PlayerActivity::class.java).putExtra(EXTRA_URL, url)
            if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
