package com.molina.suite.feature.settings.x11

import java.io.File
import java.io.IOException

/**
 * Membaca /proc. Proses dengan UID aplikasi (termasuk yang berjalan di dalam proot Debian,
 * yang memakai PID host) terbaca tanpa izin khusus. Memblokir I/O: panggil dari Dispatchers.IO.
 */
internal object ProcTable {

    fun scan(procRoot: File = File("/proc")): List<ProcEntry> {
        val dirs = procRoot.listFiles() ?: return emptyList()
        val out = ArrayList<ProcEntry>(dirs.size)
        for (dir in dirs) {
            val pid = dir.name.toIntOrNull() ?: continue
            val stat = readFile(File(dir, "stat")) ?: continue
            val fields = ProcParser.parseStat(stat) ?: continue
            val comm = readFile(File(dir, "comm"))?.trim().orEmpty()
            val args = readFile(File(dir, "cmdline"))
                ?.split('\u0000')
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            out += ProcEntry(pid, fields.ppid, fields.sid, fields.state, comm, args)
        }
        return out
    }

    fun rssKb(pid: Int, procRoot: File = File("/proc")): Long =
        readFile(File(procRoot, "$pid/status"))?.let(ProcParser::parseRssKb) ?: 0L

    private fun readFile(file: File): String? = try {
        file.inputStream().use { String(it.readBytes(), Charsets.ISO_8859_1) }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
