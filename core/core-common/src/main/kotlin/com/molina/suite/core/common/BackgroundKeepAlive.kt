package com.molina.suite.core.common

/**
 * Kontrak untuk menjaga CPU tetap aktif selama engine menjalankan proses latar
 * belakang berumur panjang (misalnya server). Pemanggilan berulang aman.
 */
interface BackgroundKeepAlive {
    fun acquire()
    fun release()
}
