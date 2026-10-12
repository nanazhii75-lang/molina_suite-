package com.molina.suite.feature.settings.debian

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Menjawab apakah sesi Terminal sedang berada di dalam Debian. Skrip login Molina menulis PID
 * shell host ke files/home/.molina/in-debian saat masuk dan menghapusnya saat keluar. PID yang
 * sudah mati (sesi ditutup paksa) dianggap di luar Debian.
 */
internal object DebianSession {

    private const val MARKER_RELATIVE = "home/.molina/in-debian"

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun isInside(context: Context): Boolean {
        val pid = readPid(File(context.filesDir, MARKER_RELATIVE)) ?: return false
        return File("/proc/$pid").exists()
    }

    private fun readPid(marker: File): Int? {
        return try {
            if (!marker.isFile) return null
            marker.readText(Charsets.UTF_8).trim().toIntOrNull()?.takeIf { it > 0 }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
