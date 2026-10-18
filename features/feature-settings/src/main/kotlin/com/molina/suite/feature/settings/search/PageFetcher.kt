package com.molina.suite.feature.settings.search

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

sealed interface FetchResult {
    data class Page(val body: String) : FetchResult
    data class Error(val reason: SearchFailure, val httpCode: Int = 0) : FetchResult
}

/** Pengambil halaman web; dipisah dari mesin pencari agar mesin bisa diuji tanpa jaringan. */
interface PageFetcher {
    suspend fun postForm(url: String, form: Map<String, String>): FetchResult
}

/**
 * Pengambil halaman yang dikeraskan: hanya HTTPS ke host yang diizinkan, tanpa cookie, tanpa cache,
 * tanpa Referer, tanpa mengikuti pengalihan ke host lain, ukuran balasan dibatasi, dan pesan galat tidak
 * pernah memuat kata kunci (log aplikasi direkam ke berkas).
 */
class HardenedPageFetcher(private val allowedHosts: Set<String>) : PageFetcher {

    override suspend fun postForm(url: String, form: Map<String, String>): FetchResult =
        withContext(Dispatchers.IO) {
            val active = AtomicReference<HttpURLConnection?>(null)
            val handle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause != null) active.get()?.disconnect()
            }
            try {
                execute(url, encode(form), active)
            } finally {
                handle.dispose()
            }
        }

    private fun execute(startUrl: String, body: ByteArray, active: AtomicReference<HttpURLConnection?>): FetchResult {
        var target = startUrl
        var post = true
        var redirects = 0
        while (true) {
            val url = try {
                URL(target)
            } catch (e: IOException) {
                return FetchResult.Error(SearchFailure.UNSAFE_REDIRECT)
            }
            val hostAllowed = url.host.lowercase(Locale.ROOT) in allowedHosts
            if (!url.protocol.equals("https", ignoreCase = true) || !hostAllowed || url.userInfo != null) {
                return FetchResult.Error(SearchFailure.UNSAFE_REDIRECT)
            }
            val connection = try {
                url.openConnection() as HttpURLConnection
            } catch (e: IOException) {
                return FetchResult.Error(SearchFailure.NETWORK)
            }
            active.set(connection)
            try {
                configure(connection, if (post) body else null)
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?: return FetchResult.Error(SearchFailure.SERVER, code)
                    redirects++
                    if (redirects > MAX_REDIRECTS) return FetchResult.Error(SearchFailure.UNSAFE_REDIRECT)
                    target = URL(url, location).toString()
                    post = false
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) return FetchResult.Error(SearchFailure.SERVER, code)
                return readBody(connection)
            } catch (e: SocketTimeoutException) {
                return FetchResult.Error(SearchFailure.TIMEOUT)
            } catch (e: IOException) {
                return FetchResult.Error(SearchFailure.NETWORK)
            } finally {
                connection.disconnect()
                active.set(null)
            }
        }
    }

    private fun configure(connection: HttpURLConnection, body: ByteArray?) {
        connection.requestMethod = if (body != null) "POST" else "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
        connection.setRequestProperty("DNT", "1")
        connection.setRequestProperty("Sec-GPC", "1")
        connection.setRequestProperty("Connection", "close")
        if (body != null) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body) }
        }
    }

    private fun readBody(connection: HttpURLConnection): FetchResult {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        connection.inputStream.use { stream ->
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BODY_BYTES) return FetchResult.Error(SearchFailure.TOO_LARGE)
                out.write(buffer, 0, n)
            }
        }
        return FetchResult.Page(out.toString("UTF-8"))
    }

    private fun encode(form: Map<String, String>): ByteArray =
        form.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }.toByteArray(Charsets.UTF_8)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_BODY_BYTES = 1_500_000
        const val MAX_REDIRECTS = 2
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val USER_AGENT = "Mozilla/5.0 (Android 8.0.0; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0"
    }
}
