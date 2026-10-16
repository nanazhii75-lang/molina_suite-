package com.molina.suite.mpv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Bubble pratinjau di atas seekbar saat digeser: waktu tujuan dan, untuk berkas lokal, satu
 * frame video. Frame diambil lewat [MediaMetadataRetriever] di thread terpisah (keyframe
 * terdekat agar cepat), hanya permintaan terbaru yang diproses, dan hasilnya disimpan per
 * detik. Stream online dan audio tidak punya frame lokal, jadi bubble hanya menampilkan waktu.
 * Pemutaran mpv tidak disentuh.
 */
internal class SeekPreviewController(
    private val context: Context,
    private val host: ViewGroup,
    private val seekBar: SeekBar,
    private val precision: Int,
) {

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Color.BLACK)
    }

    private val timeText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(2))
    }

    private val bubble = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = GradientDrawable().apply {
            setColor(0xE6000000.toInt())
            cornerRadius = dp(8).toFloat()
        }
        addView(image, LinearLayout.LayoutParams(dp(THUMB_WIDTH_DP), dp(THUMB_HEIGHT_DP)))
        addView(timeText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "seek-preview").apply { isDaemon = true }
    }
    private val cache = LruCache<Long, Bitmap>(CACHE_FRAMES)
    private val latestMs = AtomicLong(-1L)
    private val busy = AtomicBoolean(false)

    @Volatile private var active = false
    @Volatile private var source: String? = null
    @Volatile private var framesAvailable = false
    private var cacheSource: String? = null

    // hanya dipakai di thread worker
    private var retriever: MediaMetadataRetriever? = null
    private var retrieverSource: String? = null

    /** Jari menyentuh seekbar. */
    fun onStart() {
        active = true
        val newSource = resolveSource()
        if (newSource != cacheSource) {
            cache.evictAll()
            cacheSource = newSource
        }
        source = newSource
        framesAvailable = newSource != null
        image.setImageDrawable(null)
        image.visibility = if (framesAvailable) View.VISIBLE else View.GONE
        if (bubble.parent == null) {
            host.addView(bubble, RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        bubble.visibility = View.INVISIBLE
    }

    /** Seekbar digeser oleh pengguna ke [progress] (satuan seekbar). */
    fun onMove(progress: Int) {
        if (!active) return
        val ms = progress * 1000L / precision
        timeText.text = formatTime(ms)
        position(progress)
        if (framesAvailable) request(ms)
    }

    /** Jari dilepas. */
    fun onStop() {
        active = false
        (bubble.parent as? ViewGroup)?.removeView(bubble)
        worker.execute { closeRetriever() }
    }

    /** Dipanggil saat activity dihancurkan. */
    fun release() {
        active = false
        (bubble.parent as? ViewGroup)?.removeView(bubble)
        worker.execute { closeRetriever() }
        worker.shutdown()
        cache.evictAll()
    }

    // ---------------------------------------------------------------- sumber

    /** Path yang bisa dibaca MediaMetadataRetriever; null bila bukan berkas lokal. */
    private fun resolveSource(): String? {
        val path = MPVLib.getPropertyString("path") ?: return null
        return when {
            path.startsWith("fd://") ->
                path.removePrefix("fd://").toIntOrNull()?.let { "/proc/self/fd/$it" }
            path.startsWith("file://") -> Uri.parse(path).path
            path.startsWith("/") -> path
            else -> null
        }
    }

    // ---------------------------------------------------------------- posisi bubble

    private fun position(progress: Int) {
        bubble.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = bubble.measuredWidth
        val h = bubble.measuredHeight
        val seekLoc = IntArray(2)
        val hostLoc = IntArray(2)
        seekBar.getLocationInWindow(seekLoc)
        host.getLocationInWindow(hostLoc)
        val trackWidth = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
        val fraction = if (seekBar.max > 0) progress.toFloat() / seekBar.max else 0f
        val centerX = seekLoc[0] - hostLoc[0] + seekBar.paddingLeft + fraction * trackWidth
        val maxX = max(host.width - w, 0).toFloat()
        bubble.x = (centerX - w / 2f).coerceIn(0f, maxX)
        bubble.y = max(seekLoc[1] - hostLoc[1] - h - dp(8), 0).toFloat()
        bubble.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- pengambilan frame

    private fun request(ms: Long) {
        latestMs.set(ms)
        val cached = cache.get(ms / 1000L)
        if (cached != null) {
            image.setImageBitmap(cached)
            return
        }
        if (busy.compareAndSet(false, true)) worker.execute { drain() }
    }

    /** Memproses permintaan terbaru sampai tidak ada yang berubah. */
    private fun drain() {
        var last = -1L
        try {
            while (active) {
                val target = latestMs.get()
                last = target
                val key = target / 1000L
                val frame = cache.get(key) ?: fetch(target, key)
                if (frame != null) main.post { if (active) image.setImageBitmap(frame) }
                if (latestMs.get() == target) break
            }
        } finally {
            busy.set(false)
            if (active && latestMs.get() != last && busy.compareAndSet(false, true)) {
                try {
                    worker.execute { drain() }
                } catch (e: java.util.concurrent.RejectedExecutionException) {
                    busy.set(false)
                }
            }
        }
    }

    private fun fetch(ms: Long, key: Long): Bitmap? {
        val src = source ?: return null
        try {
            val r = retrieverFor(src)
            val full = r.getFrameAtTime(ms * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (full == null) {
                noFrames()
                return null
            }
            val scaled = scale(full)
            cache.put(key, scaled)
            return scaled
        } catch (e: RuntimeException) {
            Log.w(TAG, "Gagal mengambil frame pratinjau", e)
            noFrames()
            return null
        }
    }

    private fun retrieverFor(src: String): MediaMetadataRetriever {
        val existing = retriever
        if (existing != null && retrieverSource == src) return existing
        closeRetriever()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(src)
        } catch (e: RuntimeException) {
            r.release()
            throw e
        }
        retriever = r
        retrieverSource = src
        return r
    }

    private fun closeRetriever() {
        val r = retriever ?: return
        retriever = null
        retrieverSource = null
        try {
            r.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Gagal menutup retriever", e)
        }
    }

    /** Tidak ada frame (audio atau format tak terbaca): tampilkan waktu saja. */
    private fun noFrames() {
        framesAvailable = false
        main.post {
            if (!active) return@post
            image.visibility = View.GONE
            position(seekBar.progress)
        }
    }

    private fun scale(full: Bitmap): Bitmap {
        val ratio = THUMB_MAX_PX.toFloat() / max(full.width, full.height)
        if (ratio >= 1f) return full
        val scaled = Bitmap.createScaledBitmap(
            full, max((full.width * ratio).toInt(), 1), max((full.height * ratio).toInt(), 1), true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private companion object {
        const val TAG = "SeekPreview"
        const val THUMB_WIDTH_DP = 160
        const val THUMB_HEIGHT_DP = 90
        const val THUMB_MAX_PX = 320
        const val CACHE_FRAMES = 32
    }
}
