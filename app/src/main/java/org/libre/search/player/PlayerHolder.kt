@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.libre.search.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream

/** One choice in the quality menu. */
data class Quality(val label: String, val height: Int, val video: VideoStream?, val audio: AudioStream?)

object PlayerHolder {
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(ctx.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player = p
        return p
    }

    fun release() {
        player?.release()
        player = null
    }
}

object StreamSources {

    private fun generic(ctx: Context): DataSource.Factory = DefaultDataSource.Factory(
        ctx, DefaultHttpDataSource.Factory().setUserAgent(NpDownloader.UA).setAllowCrossProtocolRedirects(true)
    )

    private val ytDash get() = YoutubeHttpDataSource.Factory().setRangeParameterEnabled(true).setRnParameterEnabled(true)
    private val ytProgressive get() = YoutubeHttpDataSource.Factory().setRangeParameterEnabled(false).setRnParameterEnabled(true)
    private val ytHls get() = YoutubeHttpDataSource.Factory().setRangeParameterEnabled(false).setRnParameterEnabled(false)

    fun isYouTube(info: StreamInfo) = info.serviceId == ServiceList.YouTube.serviceId

    fun qualities(info: StreamInfo): List<Quality> {
        val audio = bestAudio(info)
        val out = ArrayList<Quality>()
        val videoOnly = info.videoOnlyStreams.filter { it.height > 0 }
        val muxed = info.videoStreams.filter { it.height > 0 }
        val byLabel = LinkedHashMap<String, Quality>()
        // Prefer MP4 (widest hardware support), then WebM.
        val ordered = (videoOnly.sortedBy { if (it.format == MediaFormat.MPEG_4) 0 else 1 }.map { it to true } +
            muxed.map { it to false })
        for ((v, isVideoOnly) in ordered) {
            if (isVideoOnly && audio == null) continue
            val label = v.resolution.ifBlank { "${v.height}p" }
            if (label in byLabel) continue
            byLabel[label] = Quality(label, v.height, v, if (isVideoOnly) audio else null)
        }
        out += byLabel.values.sortedByDescending { it.height }
        if (audio != null) out += Quality("Audio only", 0, null, audio)
        return out
    }

    fun defaultQuality(list: List<Quality>, audioOnly: Boolean): Quality? {
        if (audioOnly) list.firstOrNull { it.video == null }?.let { return it }
        return list.filter { it.video != null && it.height <= 1080 }.maxByOrNull { it.height }
            ?: list.firstOrNull()
    }

    fun bestAudio(info: StreamInfo): AudioStream? {
        val list = info.audioStreams.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
        // Skip dubbed/descriptive tracks when an original one exists.
        val original = list.filter { s ->
            val t = try { s.audioTrackType?.name } catch (_: Throwable) { null }
            t == null || t == "ORIGINAL"
        }.ifEmpty { list }
        return original.sortedWith(
            compareByDescending<AudioStream> { if (it.format == MediaFormat.M4A) 1 else 0 }
                .thenByDescending { it.averageBitrate }
        ).firstOrNull()
    }

    fun metadata(info: StreamInfo): MediaMetadata = MediaMetadata.Builder()
        .setTitle(info.name)
        .setArtist(info.uploaderName)
        .setArtworkUri(info.thumbnails.maxByOrNull { it.height }?.url?.let { Uri.parse(it) })
        .build()

    fun build(ctx: Context, info: StreamInfo, q: Quality?): MediaSource {
        val meta = metadata(info)
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live || q == null) {
            val hls = info.hlsUrl
            if (!hls.isNullOrBlank()) {
                return HlsMediaSource.Factory(if (isYouTube(info)) ytHls else generic(ctx))
                    .createMediaSource(item(hls, meta))
            }
            val dash = info.dashMpdUrl
            if (!dash.isNullOrBlank()) {
                return DashMediaSource.Factory(DefaultDashChunkSource.Factory(generic(ctx)), generic(ctx))
                    .createMediaSource(item(dash, meta))
            }
            throw IllegalStateException("No playable stream found")
        }
        val parts = listOfNotNull(q.video, q.audio).map { forStream(ctx, it, info, meta) }
        return if (parts.size == 1) parts[0] else MergingMediaSource(true, *parts.toTypedArray())
    }

    private fun item(url: String, meta: MediaMetadata) =
        MediaItem.Builder().setUri(Uri.parse(url)).setMediaMetadata(meta).build()

    private fun forStream(ctx: Context, s: Stream, info: StreamInfo, meta: MediaMetadata): MediaSource {
        val content = s.content
        val yt = isYouTube(info) && info.streamType == StreamType.VIDEO_STREAM
        if (yt) {
            val itag = s.itagItem
            when (s.deliveryMethod) {
                DeliveryMethod.PROGRESSIVE_HTTP -> {
                    val adaptive = (s is VideoStream && s.isVideoOnly) || s is AudioStream
                    if (adaptive && itag != null) {
                        try {
                            val m = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(content, itag, info.duration)
                            return dashFromText(m, content, meta)
                        } catch (_: Exception) {
                        }
                    }
                    return ProgressiveMediaSource.Factory(ytProgressive).createMediaSource(item(content, meta))
                }
                DeliveryMethod.DASH -> {
                    if (itag != null) {
                        val m = YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(content, itag, info.duration)
                        return dashFromText(m, content, meta)
                    }
                }
                DeliveryMethod.HLS -> return HlsMediaSource.Factory(ytHls).createMediaSource(item(content, meta))
                else -> {}
            }
        }
        val g = generic(ctx)
        return when (s.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> ProgressiveMediaSource.Factory(g).createMediaSource(item(content, meta))
            DeliveryMethod.HLS -> {
                val url = if (s.isUrl) content else s.manifestUrl ?: content
                HlsMediaSource.Factory(g).createMediaSource(item(url, meta))
            }
            DeliveryMethod.DASH -> {
                if (s.isUrl) DashMediaSource.Factory(DefaultDashChunkSource.Factory(g), g).createMediaSource(item(content, meta))
                else {
                    val manifest = DashManifestParser().parse(
                        Uri.parse(s.manifestUrl ?: ""), ByteArrayInputStream(content.toByteArray())
                    )
                    DashMediaSource.Factory(DefaultDashChunkSource.Factory(g), g).createMediaSource(
                        manifest, MediaItem.Builder().setUri(Uri.parse(s.manifestUrl ?: "")).setMediaMetadata(meta).build()
                    )
                }
            }
            else -> throw IllegalStateException("Unsupported stream type ${s.deliveryMethod}")
        }
    }

    private fun dashFromText(manifest: String, url: String, meta: MediaMetadata): MediaSource {
        val parsed = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(manifest.toByteArray()))
        val f = ytDash
        return DashMediaSource.Factory(DefaultDashChunkSource.Factory(f), f)
            .createMediaSource(parsed, MediaItem.Builder().setUri(Uri.parse(url)).setMediaMetadata(meta).build())
    }
}
