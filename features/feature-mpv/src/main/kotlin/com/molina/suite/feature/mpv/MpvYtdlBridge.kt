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
    private const val PREFIX_WAIT_MS = 30L * 60L * 1000L
    private const val PREFIX_POLL_MS = 2000L

    /**
     * Target perangkat Android 8 (tanpa dekoder AV1 perangkat keras): hindari AV1 dan batasi
     * 1080p. Format tanpa info codec/tinggi tetap boleh (tanda ? setelah operator), dan
     * alternatif terakhir "best" menjaga situs lain tetap bisa diputar.
     */
    private const val YTDL_FORMAT =
        "bestvideo[height<=?1080][vcodec!*=?av01]+bestaudio/" +
            "best[height<=?1080][vcodec!*=?av01]/best"

    fun install(application: Application) {
        val target = wrapperFile(application)
        // Jalur cepat YouTube: pembungkus bertanya ke server lokal ini sebelum memakai yt-dlp di proot.
        NewPipeYtdlServer.start(application)
        MolinaMpvOptions.set("ytdl", "yes")
        MolinaMpvOptions.set("ytdl-format", YTDL_FORMAT)
        // Log mpv lengkap (termasuk ytdl_hook dan jaringan) agar kegagalan putar bisa dibaca dari
        // Terminal molina; ditimpa setiap pemutar baru dibuat. Disimpan di usr/var/log karena
        // Terminal berjalan di Debian/proot dan hanya melihat isi usr/ (bukan akar files/).
        mpvLogFile(application)?.let {
            MolinaMpvOptions.set("log-file", it.absolutePath)
            MolinaMpvOptions.set("msg-level", "all=v")
        }
        // Nilai script-opts tidak boleh memuat : = % [ ] \\ " ' (penanda sintaks daftar opsi mpv);
        // hanya jalur pembungkus yang aman di sini. Penyaringan URL media langsung ada di molina-ytdlp.
        MolinaMpvOptions.set("script-opts", "ytdl_hook-ytdl_path=" + target.absolutePath)
        Thread({ writeWrapperWhenPrefixReady(application, target) }, "mpv-ytdl-bridge")
            .apply { isDaemon = true }
            .start()
    }

    /**
     * Lokasi log mpv di usr/var/log. Null sebelum bootstrap selesai (usr/bin/bash belum ada),
     * supaya usr/ tidak terbuat lebih dulu dan prefix tampak sudah terpasang.
     */
    private fun mpvLogFile(application: Application): File? {
        if (!File(application.filesDir, "usr/bin/bash").isFile) return null
        val dir = File(application.filesDir, "usr/var/log")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return File(dir, "mpv.log")
    }

    /** Lokasi pembungkus molina-ytdlp di prefix aplikasi. */
    fun wrapperFile(application: Application): File =
        File(application.filesDir, "usr/bin/$WRAPPER")

    /**
     * Menunggu bootstrap selesai (usr/bin/bash ada) sebelum menulis pembungkus. Membuat
     * usr/bin lebih dulu membuat prefix tampak sudah terpasang dan melewati pemasangan bootstrap.
     */
    private fun writeWrapperWhenPrefixReady(application: Application, target: File) {
        val bash = File(application.filesDir, "usr/bin/bash")
        val deadline = System.currentTimeMillis() + PREFIX_WAIT_MS
        while (!bash.isFile) {
            if (System.currentTimeMillis() >= deadline) {
                Log.w(TAG, "Bootstrap belum selesai; pembungkus ytdl tidak ditulis")
                return
            }
            try {
                Thread.sleep(PREFIX_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
        writeWrapper(application, target)
        target.parentFile?.let { MolinaCommandScripts.install(application, it) }
    }

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
