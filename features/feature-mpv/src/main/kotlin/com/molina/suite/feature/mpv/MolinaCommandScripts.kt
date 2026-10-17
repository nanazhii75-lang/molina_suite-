package com.molina.suite.feature.mpv

import android.app.Application
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Memasang perintah Terminal molina-play dan molina-cari (aset) ke usr/bin prefix aplikasi.
 * Dipanggil setelah bootstrap selesai; berkas yang isinya sudah sama tidak ditulis ulang.
 */
internal object MolinaCommandScripts {

    private const val TAG = "MolinaCommandScripts"
    private val SCRIPTS = listOf("molina-play", "molina-cari")

    fun install(application: Application, binDir: File) {
        for (name in SCRIPTS) {
            try {
                write(application, binDir, name)
            } catch (e: IOException) {
                Log.w(TAG, "Gagal memasang $name", e)
            }
        }
    }

    private fun write(application: Application, binDir: File, name: String) {
        val content = application.assets.open(name).use { it.readBytes() }
        if (!binDir.isDirectory && !binDir.mkdirs()) {
            Log.w(TAG, "Tidak bisa membuat $binDir")
            return
        }
        val target = File(binDir, name)
        if (target.isFile && target.canExecute() && target.readBytes().contentEquals(content)) return
        val tmp = File(binDir, "$name.tmp")
        tmp.writeBytes(content)
        if (!tmp.setExecutable(true, false) || !tmp.renameTo(target)) {
            tmp.delete()
            Log.w(TAG, "Gagal memasang $target")
        }
    }
}
