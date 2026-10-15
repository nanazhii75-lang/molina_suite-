package com.molina.suite.core.common

/**
 * Papan permintaan keluar dari aplikasi: fitur mana pun (mis. tombol Exit di notifikasi Terminal)
 * meminta, shell yang menutup seluruh aplikasi tanpa fitur mengenal kelas shell. Shell memasang
 * penangannya lewat [setHandler]; tanpa penangan, [request] tidak berbuat apa-apa.
 */
class AppExitBoard {

    @Volatile
    private var handler: (() -> Unit)? = null

    /** Memasang (atau melepas dengan null) penangan yang menutup aplikasi. */
    fun setHandler(handler: (() -> Unit)?) {
        this.handler = handler
    }

    /** True bila sudah ada penangan yang bisa menutup aplikasi. */
    fun isHandled(): Boolean = handler != null

    /** Meminta aplikasi ditutup; true bila penangan dipanggil (proses biasanya sudah mati sesudahnya). */
    fun request(): Boolean {
        val target = handler ?: return false
        target()
        return true
    }
}
