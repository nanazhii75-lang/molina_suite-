package com.molina.suite.feature.settings.search

import com.molina.suite.core.common.ConnectionLedger
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * Pengambil halaman yang dikeraskan di atas [SecureClients]: hanya HTTPS ke host yang diizinkan,
 * pengalihan diikuti manual dan tidak boleh pindah host, ukuran balasan dibatasi, dan pesan galat tidak
 * pernah memuat kata kunci (log aplikasi direkam ke berkas).
 */
class OkHttpPageFetcher(
    private val allowedHosts: Set<String>,
    private val client: OkHttpClient,
    private val onClose: () -> Unit = {}
) : PageFetcher, AutoCloseable {

    private sealed interface Step {
        data class Done(val result: FetchResult) : Step
        data class Follow(val next: HttpUrl) : Step
    }

    override suspend fun postForm(url: String, form: Map<String, String>): FetchResult =
        withContext(Dispatchers.IO) {
            val active = AtomicReference<Call?>(null)
            val handle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause != null) active.get()?.cancel()
            }
            try {
                execute(url, form, active)
            } finally {
                handle.dispose()
            }
        }

    private fun execute(startUrl: String, form: Map<String, String>, active: AtomicReference<Call?>): FetchResult {
        var target = startUrl.toHttpUrlOrNull() ?: return FetchResult.Error(SearchFailure.UNSAFE_REDIRECT)
        var post = true
        var redirects = 0
        while (true) {
            if (!TargetPolicy.allows(target, allowedHosts)) {
                return FetchResult.Error(SearchFailure.UNSAFE_REDIRECT)
            }
            when (val step = once(buildRequest(target, if (post) form else null), active)) {
                is Step.Done -> return step.result
                is Step.Follow -> {
                    redirects++
                    if (redirects > MAX_REDIRECTS) return FetchResult.Error(SearchFailure.UNSAFE_REDIRECT)
                    target = step.next
                    post = false
                }
            }
        }
    }

    private fun once(request: Request, active: AtomicReference<Call?>): Step {
        val call = client.newCall(request)
        active.set(call)
        return try {
            call.execute().use { response -> interpret(response) }
        } catch (e: IOException) {
            Step.Done(FetchResult.Error(FailureClassifier.classify(e)))
        } finally {
            active.set(null)
        }
    }

    private fun interpret(response: Response): Step {
        val code = response.code
        if (code in REDIRECT_CODES) {
            val next = response.header("Location")?.let { response.request.url.resolve(it) }
            return if (next == null) Step.Done(FetchResult.Error(SearchFailure.SERVER, code)) else Step.Follow(next)
        }
        if (code != 200) return Step.Done(FetchResult.Error(SearchFailure.SERVER, code))
        return Step.Done(readBody(response))
    }

    private fun readBody(response: Response): FetchResult {
        val body = response.body ?: return FetchResult.Error(SearchFailure.SERVER, response.code)
        if (body.contentLength() > MAX_BODY_BYTES) return FetchResult.Error(SearchFailure.TOO_LARGE)
        val source = body.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, READ_CHUNK)
            if (read < 0) break
            total += read
            if (total > MAX_BODY_BYTES) return FetchResult.Error(SearchFailure.TOO_LARGE)
        }
        return FetchResult.Page(buffer.readUtf8())
    }

    private fun buildRequest(url: HttpUrl, form: Map<String, String>?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("DNT", "1")
            .header("Sec-GPC", "1")
        if (form == null) {
            builder.get()
        } else {
            val body = FormBody.Builder()
            for ((key, value) in form) body.add(key, value)
            builder.post(body.build())
        }
        return builder.build()
    }

    override fun close() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        onClose()
    }

    companion object {
        private const val MAX_BODY_BYTES = 1_500_000L
        private const val READ_CHUNK = 8_192L
        private const val MAX_REDIRECTS = 2
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val USER_AGENT = "Mozilla/5.0 (Android 8.0.0; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0"

        /** Pengambil untuk DuckDuckGo: DoH, pin sertifikat, TLS 1.2+, proxy sistem diabaikan. */
        fun duckDuckGo(
            ledger: ConnectionLedger = ConnectionLedger.shared,
            proxy: Proxy = Proxy.NO_PROXY
        ): OkHttpPageFetcher {
            val bootstrap = SecureClients.dohBootstrap()
            val dns = SecureDns.create(bootstrap, ledger)
            val client = SecureClients.pinned(dns, DuckDuckGoPins.pinner(), proxy)
            return OkHttpPageFetcher(setOf(DuckDuckGoSearch.HOST), client) {
                bootstrap.connectionPool.evictAll()
                bootstrap.dispatcher.executorService.shutdown()
            }
        }
    }
}
