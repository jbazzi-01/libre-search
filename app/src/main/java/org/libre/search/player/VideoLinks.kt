package org.libre.search.player

import org.libre.search.core.Text
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService

/** Decides which links open in the app's own ad-free player. */
object VideoLinks {
    private val hosts = listOf("youtube.com", "youtu.be", "youtube-nocookie.com", "soundcloud.com", "bandcamp.com")

    fun isStream(url: String): Boolean {
        val host = Text.host(url)
        if (host.isEmpty()) return false
        val candidate = hosts.any { Text.hostMatches(host, it) } ||
            url.contains("/videos/watch/") || Regex("/w/[A-Za-z0-9]{10,}").containsMatchIn(url)
        if (!candidate) return false
        return try {
            val service = NewPipe.getServiceByUrl(url)
            service.getLinkTypeByUrl(url) == StreamingService.LinkType.STREAM
        } catch (_: Throwable) {
            false
        }
    }
}
