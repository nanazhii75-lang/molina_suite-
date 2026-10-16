package com.molina.suite.feature.x11

import java.io.File
import java.io.IOException

/**
 * Mencari proses X server milik aplikasi ini lewat /proc. Proses dengan UID yang sama
 * terbaca tanpa izin khusus, jadi tidak perlu menghubungi socket X server (yang akan
 * dianggap sambungan baru oleh server).
 */
internal object X11ServerProbe {

    fun findPids(selfPid: Int): List<Int> {
        val entries = File("/proc").listFiles() ?: return emptyList()
        val pids = ArrayList<Int>()
        for (entry in entries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid != selfPid && isServer(pid)) pids += pid
        }
        return pids
    }

    private fun isServer(pid: Int): Boolean {
        val raw = try {
            File("/proc/$pid/cmdline").inputStream().use { it.readBytes() }
        } catch (e: IOException) {
            return false
        } catch (e: SecurityException) {
            return false
        }
        return String(raw, Charsets.ISO_8859_1)
            .split('\u0000')
            .any { it == X11ServerCommand.ENTRY_POINT }
    }
}
