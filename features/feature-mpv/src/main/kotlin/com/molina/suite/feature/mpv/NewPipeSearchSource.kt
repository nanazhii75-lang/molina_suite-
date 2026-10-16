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
import org.schabi.newpipe.extractor.stream.StreamInfo
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

    private class Related(val id: String, val items: List<YoutubeResult>, val builtAt: Long)

    private var relatedCache: Related? = null

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

    override fun related(watchUrl: String, limit: Int): List<YoutubeResult> =
        Activities.log.track(ActivitySource.MPV, "Video terkait (NewPipe)") { entry ->
            try {
                runRelated(watchUrl, limit).also { entry.end(true, "${it.size} video") }
            } catch (e: YoutubeSearchException) {
                if (e.cause is InterruptedException) {
                    entry.end(false, "dibatalkan", ActivityPriority.WARN)
                } else {
                    entry.end(false, MpvLogText.redact(e.message ?: e.reason.name))
                }
                throw e
            }
        }

    private fun runRelated(watchUrl: String, limit: Int): List<YoutubeResult> {
        // Hanya URL video YouTube yang dikenal; sumber lain (berkas, stream) tidak punya saran.
        val id = YoutubeWatchUrl.videoId(watchUrl) ?: return emptyList()
        val count = limit.coerceIn(1, MAX_RELATED)
        synchronized(lock) {
            val cached = relatedCache
            if (cached != null && cached.id == id &&
                SystemClock.elapsedRealtime() - cached.builtAt < POOL_TTL_MS
            ) {
                return cached.items.take(count)
            }
        }
        val built = fetchRelated(id)
        synchronized(lock) { relatedCache = Related(id, built, SystemClock.elapsedRealtime()) }
        return built.take(count)
    }

    private fun fetchRelated(id: String): List<YoutubeResult> {
        NewPipeRuntime.ensureReady()
        throwIfInterrupted()
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, WATCH_URL + id)
        } catch (e: ReCaptchaException) {
            throw YoutubeSearchException(Reason.FAILED, "YouTube meminta verifikasi (HTTP 429)", e)
        } catch (e: ExtractionException) {
            throw YoutubeSearchException(Reason.FAILED, "Gagal membaca video terkait: " + e.message, e)
        } catch (e: SocketTimeoutException) {
            throw YoutubeSearchException(Reason.TIMEOUT, "Koneksi ke YouTube melebihi batas waktu", e)
        } catch (e: InterruptedIOException) {
            throw YoutubeSearchException(Reason.FAILED, "Pengambilan dibatalkan", InterruptedException())
        } catch (e: IOException) {
            throw YoutubeSearchException(Reason.FAILED, "Jaringan: " + e.message, e)
        } catch (e: RuntimeException) {
            throw YoutubeSearchException(Reason.FAILED, "Extractor galat: " + e.message, e)
        }
        return info.relatedItems.asSequence()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { NewPipeResultMapper.map(it) }
            .filter { it.id != id }
            .distinctBy { it.id }
            .toList()
    }

    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw YoutubeSearchException(Reason.FAILED, "Pencarian dibatalkan", InterruptedException())
        }
    }

    private companion object {
        // Seluruh halaman UI terlayani dari satu kolam hasil.
        const val POOL_TARGET = YoutubeSearchController.PAGE_SIZE * YoutubeSearchController.MAX_PAGES
        const val MAX_FETCH_PAGES = 3
        const val MAX_RESULTS = 20
        const val MAX_RELATED = 20
        const val WATCH_URL = "https://www.youtube.com/watch?v="
        const val POOL_TTL_MS = 5 * 60 * 1000L
    }
}
