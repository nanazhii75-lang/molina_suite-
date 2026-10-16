package com.molina.suite.mpv

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Timer tidur: memanggil [onExpire] satu kali di thread utama setelah durasi yang dipilih.
 * Tidak tahu apa-apa tentang pemutar; Activity yang memutuskan apa yang terjadi saat habis.
 */
internal class SleepTimer(private val onExpire: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L

    private val fire = Runnable {
        deadline = 0L
        onExpire()
    }

    val isActive: Boolean
        get() = deadline != 0L

    /** Sisa waktu dibulatkan ke atas dalam menit; 0 bila timer tidak aktif. */
    fun remainingMinutes(): Int {
        if (!isActive) return 0
        val left = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        return ((left + 59_999L) / 60_000L).toInt()
    }

    /** Memulai timer baru; timer yang sedang berjalan diganti. */
    fun start(minutes: Int) {
        require(minutes > 0) { "minutes harus positif" }
        cancel()
        val ms = minutes * 60_000L
        deadline = SystemClock.elapsedRealtime() + ms
        handler.postDelayed(fire, ms)
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        deadline = 0L
    }

    companion object {
        val PRESET_MINUTES = intArrayOf(15, 30, 45, 60, 90)
    }
}
