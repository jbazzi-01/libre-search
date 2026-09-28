package org.libre.search.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, message: String) : IOException(message)

object Http {
    const val APP_UA = "LibreSearch/1.0 (personal Android app; privacy-first)"
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    lateinit var client: OkHttpClient
        private set

    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun url(base: String, params: Map<String, String?>): String {
        val b = base.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
        ua: String = APP_UA
    ): String = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(url).header("User-Agent", ua)
        headers.forEach { (k, v) -> rb.header(k, v) }
        client.newCall(rb.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code} for ${url.substringBefore('?')}")
            body
        }
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(getString(url, headers + ("Accept" to "application/json")))

    suspend fun postForm(url: String, form: Map<String, String>): String = withContext(Dispatchers.IO) {
        val fb = okhttp3.FormBody.Builder()
        form.forEach { (k, v) -> fb.add(k, v) }
        val req = Request.Builder().url(url).header("User-Agent", APP_UA).post(fb.build()).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
            body
        }
    }
}
