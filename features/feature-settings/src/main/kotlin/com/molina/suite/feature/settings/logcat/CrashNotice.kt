package com.molina.suite.feature.settings.logcat

import java.io.File

/**
 * Penanda laporan crash yang belum dibuka: laporan di [crashDir] yang lebih baru dari saat
 * pengguna terakhir membukanya ([markSeen]). Saat pertama kali dipakai ([initBaseline]) laporan
 * lama dianggap sudah dilihat, jadi hanya crash sesudahnya yang ditandai.
 */
internal class CrashNotice(
    private val crashDir: File,
    private val seenFile: File,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Jumlah laporan yang belum dibuka. */
    fun unseen(): Int {
        val seen = StateFile.read(seenFile)?.trim()?.toLongOrNull() ?: 0L
        return CrashLogStore.list(crashDir).count { it.lastModified() > seen }
    }

    /** Menandai semua laporan yang ada sekarang sudah dibuka. */
    fun markSeen() {
        StateFile.write(seenFile, clock().toString())
    }

    /** Menetapkan titik awal bila belum ada, supaya laporan dari sebelum penanda ini tidak dihitung baru. */
    fun initBaseline() {
        if (!seenFile.isFile) markSeen()
    }
}
