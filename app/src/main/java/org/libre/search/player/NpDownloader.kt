package org.libre.search.player

import okhttp3.RequestBody.Companion.toRequestBody
import org.libre.search.core.Http
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Network bridge for the NewPipe extractor, using the app's own HTTP client. */
object NpDownloader : Downloader() {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val rb = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header("User-Agent", UA)
        request.headers().forEach { (name, values) ->
            rb.removeHeader(name)
            values.forEach { rb.addHeader(name, it) }
        }
        Http.client.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val text = resp.body?.string()
            return Response(resp.code, resp.message, resp.headers.toMultimap(), text, resp.request.url.toString())
        }
    }
}
