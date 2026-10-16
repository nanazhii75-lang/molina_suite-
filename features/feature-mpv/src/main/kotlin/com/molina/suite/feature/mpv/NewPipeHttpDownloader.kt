package com.molina.suite.feature.mpv

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Lapisan HTTP untuk NewPipeExtractor. Hanya menerjemahkan [Request] Extractor ke OkHttp dan
 * hasilnya ke [Response]; tidak menyimpan cookie atau keadaan apa pun.
 */
internal class NewPipeHttpDownloader private constructor(
    private val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Permintaan dibatalkan")
        }
        val method = request.httpMethod()
        val payload = request.dataToSend()
        val body = if (method in METHODS_WITH_BODY) {
            (payload ?: ByteArray(0)).toRequestBody(null)
        } else {
            null
        }
        val builder = try {
            okhttp3.Request.Builder().url(request.url()).method(method, body)
        } catch (e: IllegalArgumentException) {
            throw IOException("Permintaan tidak valid: " + e.message, e)
        }
        builder.header("User-Agent", USER_AGENT)
        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (value in values) builder.addHeader(name, value)
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("YouTube meminta verifikasi (HTTP 429)", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }

    companion object {
        internal const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

        fun create(): NewPipeHttpDownloader = NewPipeHttpDownloader(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        )
    }
}
