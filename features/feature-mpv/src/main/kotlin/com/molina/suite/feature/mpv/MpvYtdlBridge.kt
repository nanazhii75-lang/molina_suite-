package com.molina.suite.feature.mpv

import android.app.Application
import android.util.Log
import com.molina.suite.mpv.MolinaMpvOptions
import java.io.File
import java.io.IOException

/**
 * Menyiapkan pemutaran URL (YouTube dll.) lewat ytdl_hook mpv. Pembungkus
 * molina-ytdlp (asset) menjalankan yt-dlp di dalam Debian/proot-distro dan
 * ditulis ke prefix aplikasi; mpv diarahkan ke pembungkus itu lewat opsi.
 */
internal object MpvYtdlBridge {

    private const val TAG = "MpvYtdlBridge"
    private const val WRAPPER = "molina-ytdlp"

    fun install(application: Application) {
        val target = wrapperFile(application)
        MolinaMpvOptions.set("ytdl", "yes")
        MolinaMpvOptions.set("script-opts", "ytdl_hook-ytdl_path=" + target.absolutePath)
        Thread({ writeWrapper(application, target) }, "mpv-ytdl-bridge").start()
    }

    /** Lokasi pembungkus molina-ytdlp di prefix aplikasi. */
    fun wrapperFile(application: Application): File =
        File(application.filesDir, "usr/bin/$WRAPPER")

    private fun writeWrapper(application: Application, target: File) {
        try {
            val content = application.assets.open(WRAPPER).use { it.readBytes() }
            val dir = target.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) {
                Log.w(TAG, "Tidak bisa membuat " + dir)
                return
            }
            if (target.isFile && target.canExecute() && target.readBytes().contentEquals(content)) return
            val tmp = File(dir, WRAPPER + ".tmp")
            tmp.writeBytes(content)
            if (!tmp.setExecutable(true, false) || !tmp.renameTo(target)) {
                tmp.delete()
                Log.w(TAG, "Gagal memasang pembungkus " + target)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Gagal menulis pembungkus ytdl", e)
        }
    }
}
