package com.molina.suite.mpv.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Pemuat thumbnail ringan: unduh di thread latar, cache LRU di memori, tanpa pustaka tambahan. */
internal object ThumbnailLoader {

    private const val TAG = "ThumbnailLoader"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val TARGET_WIDTH = 320

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3) { task ->
        Thread(task, "molina-thumb").apply { isDaemon = true }
    }
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun cacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 16L)
            .coerceIn(2L * 1024 * 1024, 32L * 1024 * 1024)
            .toInt()

    /** Memuat [url] ke [view]; tag pada view mencegah gambar lama menimpa item yang didaur ulang. */
    fun load(view: ImageView, url: String) {
        view.tag = url
        val cached = cache.get(url)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        pool.execute {
            val bitmap = download(url) ?: return@execute
            cache.put(url, bitmap)
            main.post { if (view.tag == url) view.setImageBitmap(bitmap) }
        }
    }

    private fun download(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { it.readLimited(MAX_BYTES) } ?: return null
            decode(bytes)
        } catch (e: IOException) {
            Log.w(TAG, "Gagal mengunduh thumbnail: " + e.message)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun InputStream.readLimited(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
