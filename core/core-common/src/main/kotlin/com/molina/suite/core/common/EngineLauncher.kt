package com.molina.suite.core.common

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Kontrak untuk engine yang tidak punya tab: dijalankan dari titik masuk lain
 * (mis. tombol di home mpv) dan membuka jendelanya sendiri. Shell dan modul
 * lain hanya mengenal interface ini, bukan kelas engine.
 */
interface EngineLauncher {
    val id: EngineId
    val status: StateFlow<EngineStatus>

    /** Pesan galat terakhir untuk ditampilkan ke pengguna; null bila tidak ada galat. */
    val lastError: StateFlow<String?>

    /** Menjalankan engine lalu membuka jendelanya. Aman dipanggil berulang. */
    fun launch(host: Activity)

    /** Menghentikan engine dan menutup jendelanya. */
    fun stop()
}
