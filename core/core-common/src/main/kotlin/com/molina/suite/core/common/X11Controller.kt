package com.molina.suite.core.common

/** Hasil menghentikan X11 sampai tuntas: jumlah proses yang dihentikan dan sisa pid yang membandel. */
data class X11StopResult(
    val stopped: Int,
    val remaining: Set<Int>,
    val freedKb: Long
) {
    val isClean: Boolean get() = remaining.isEmpty()
}

/**
 * Kontrak pemantau dan penghenti X11 yang dipakai notifikasi X11. Pelaksananya modul Setting
 * (yang memegang pemindai proses), jadi modul X11 dan Setting tidak saling bergantung.
 */
interface X11Controller {

    /** Perkiraan memori fisik (KB) yang dipakai desktop dan server X11; 0 bila tidak berjalan. */
    suspend fun memoryKb(): Long

    /** Menghentikan desktop dan server sampai tuntas (SIGTERM, lalu SIGKILL) dan memeriksa ulang. */
    suspend fun stopAll(): X11StopResult
}
