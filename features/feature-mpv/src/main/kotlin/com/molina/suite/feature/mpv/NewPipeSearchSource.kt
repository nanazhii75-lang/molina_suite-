package com.molina.suite.feature.mpv

import android.os.SystemClock
import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivityPriority
import com.molina.suite.core.common.ActivitySource
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeSearchController
import com.molina.suite.mpv.search.YoutubeSearchException
import com.molina.suite.mpv.search.YoutubeSearchException.Reason
import com.molina.suite.mpv.search.YoutubeSearchSource
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale

/**
 * Pencarian YouTube lewat NewPipeExtractor. YouTube tidak menyediakan pengurutan tanggal lewat
 * Extractor, jadi satu kolam hasil (sampai [POOL_TARGET] video dari beberapa halaman) diambil
 * sekali, diurutkan terbaru ke terlama menurut waktu unggah, lalu dibagi per halaman UI.
 * Halaman berikutnya untuk kata kunci yang sama dilayani dari kolam tanpa permintaan jaringan.
 * Hasil tanpa waktu unggah (misalnya siaran langsung) ditaruh paling bawah; item dengan waktu
 * sama tetap berurutan menurut relevansi YouTube.
 */
internal class NewPipeSearchSource : YoutubeSearchSource {

    private class Pool(val key: String, val items: List<YoutubeResult>, val builtAt: Long)

    private val lock = Any()
    private var pool: Pool? = null

    override fun search(
        query: String,
        offset: Int,
        limit: Int,
        onProgress: (Int) -> Unit
    ): List<YoutubeResult> =
        Activities.log.track(ActivitySource.MPV, "Cari di YouTube (NewPipe)") { entry ->
            try {
                runSearch(query, offset, limit, onProgress).also { entry.end(true, "${it.size} hasil") }
            } catch (e: YoutubeSearchException) {
                if (e.cause is InterruptedException) {
                    entry.end(false, "dibatalkan", ActivityPriority.WARN)
                } else {
                    entry.end(false, MpvLogText.redact(e.message ?: e.reason.name))
                }
                throw e
            }
        }

    private fun runSearch(
        query: String,
        offset: Int,
        limit: Int,
        onProgress: (Int) -> Unit
    ): List<YoutubeResult> {
        val cleaned = query.filter { !it.isISOControl() }.trim()
        if (cleaned.isEmpty()) return emptyList()
        val start = offset.coerceAtLeast(0)
        val count = limit.coerceIn(1, MAX_RESULTS)
        val items = poolFor(cleaned, onProgress)
        if (start >= items.size) return emptyList()
        return items.subList(start, minOf(items.size, start + count)).toList()
    }

    private fun poolFor(query: String, onProgress: (Int) -> Unit): List<YoutubeResult> {
        val key = query.lowercase(Locale.ROOT)
        synchronized(lock) {
            val cached = pool
            if (cached != null && cached.key == key &&
                SystemClock.elapsedRealtime() - cached.builtAt < POOL_TTL_MS
            ) {
                return cached.items
            }
        }
        val built = fetchPool(query, onProgress)
        synchronized(lock) { pool = Pool(key, built, SystemClock.elapsedRealtime()) }
        return built
    }

    private fun fetchPool(query: String, onProgress: (Int) -> Unit): List<YoutubeResult> {
        NewPipeRuntime.ensureReady()
        val collected = LinkedHashMap<String, YoutubeResult>()
        try {
            throwIfInterrupted()
            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
            extractor.fetchPage()
            var page = extractor.initialPage
            var fetchedPages = 1
            while (true) {
                for (item in page.items) {
                    if (item !is StreamInfoItem) continue
                    val mapped = NewPipeResultMapper.map(item) ?: continue
                    collected.putIfAbsent(mapped.id, mapped)
                }
                onProgress(collected.size.coerceAtMost(YoutubeSearchController.PAGE_SIZE))
                if (collected.size >= POOL_TARGET || fetchedPages >= MAX_FETCH_PAGES || !page.hasNextPage()) {
                    break
                }
                throwIfInterrupted()
                page = extractor.getPage(page.nextPage)
                fetchedPages += 1
            }
        } catch (e: SearchExtractor.NothingFoundException) {
            // Tidak ada hasil (lagi): pakai yang sudah terkumpul.
        } catch (e: ReCaptchaException) {
            throw YoutubeSearchException(Reason.FAILED, "YouTube meminta verifikasi (HTTP 429)", e)
        } catch (e: ExtractionException) {
            throw YoutubeSearchException(Reason.FAILED, "Gagal membaca hasil YouTube: " + e.message, e)
        } catch (e: SocketTimeoutException) {
            throw YoutubeSearchException(Reason.TIMEOUT, "Koneksi ke YouTube melebihi batas waktu", e)
        } catch (e: InterruptedIOException) {
            throw YoutubeSearchException(Reason.FAILED, "Pencarian dibatalkan", InterruptedException())
        } catch (e: IOException) {
            throw YoutubeSearchException(Reason.FAILED, "Jaringan: " + e.message, e)
        } catch (e: RuntimeException) {
            // Extractor dapat melempar NPE/IllegalState bila format YouTube berubah.
            throw YoutubeSearchException(Reason.FAILED, "Extractor galat: " + e.message, e)
        }
        return collected.values
            .sortedByDescending { it.publishedAtMillis ?: Long.MIN_VALUE }
            .take(POOL_TARGET)
    }

    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw YoutubeSearchException(Reason.FAILED, "Pencarian dibatalkan", InterruptedException())
        }
    }

    private companion object {
        const val POOL_TARGET = 40
        const val MAX_FETCH_PAGES = 3
        const val MAX_RESULTS = 20
        const val POOL_TTL_MS = 5 * 60 * 1000L
    }
}
