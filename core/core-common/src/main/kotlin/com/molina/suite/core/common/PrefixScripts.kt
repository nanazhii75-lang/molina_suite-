package com.molina.suite.core.common

import android.app.Application
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Memasang skrip perintah Terminal (aset aplikasi) ke usr/bin prefix aplikasi. Modul fitur cukup
 * menyebut nama aset; pemasangan menunggu bootstrap selesai (usr/bin/bash ada) supaya usr/ tidak
 * terbuat lebih dulu dan prefix tampak sudah terpasang. Berkas yang isinya sudah sama tidak ditulis ulang.
 */
object PrefixScripts {

    private const val TAG = "PrefixScripts"
    private const val PREFIX_WAIT_MS = 30L * 60L * 1000L
    private const val PREFIX_POLL_MS = 2000L

    /** Memasang [names] di thread latar belakang; kembali segera. */
    fun installWhenReady(application: Application, names: List<String>) {
        Thread({ waitAndInstall(application, names) }, "prefix-scripts")
            .apply { isDaemon = true }
            .start()
    }

    private fun waitAndInstall(application: Application, names: List<String>) {
        val bash = File(application.filesDir, "usr/bin/bash")
        val deadline = System.currentTimeMillis() + PREFIX_WAIT_MS
        while (!bash.isFile) {
            if (System.currentTimeMillis() >= deadline) {
                Log.w(TAG, "Bootstrap belum selesai; skrip $names tidak dipasang")
                return
            }
            try {
                Thread.sleep(PREFIX_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
        val binDir = bash.parentFile ?: return
        for (name in names) {
            try {
                write(application, binDir, name)
            } catch (e: IOException) {
                Log.w(TAG, "Gagal memasang $name", e)
            }
        }
    }

    private fun write(application: Application, binDir: File, name: String) {
        val content = application.assets.open(name).use { it.readBytes() }
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
