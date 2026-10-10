package com.molina.suite.feature.code

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.IOException

/**
 * Membaca dan mengirim sinyal ke proses lewat /proc. Proses di dalam PRoot
 * adalah proses Android biasa dengan uid aplikasi, jadi bisa dilihat dan
 * dihentikan langsung dari sisi host.
 */
internal class CodeProcessInspector {

    private class ProcStat(val state: Char, val parentPid: Int)

    fun isAlive(pid: Int): Boolean {
        val stat = readStat(pid) ?: return false
        return stat.state != 'Z' && stat.state != 'X'
    }

    /** Semua keturunan [root] (anak, cucu, dan seterusnya), tanpa [root] sendiri. */
    fun descendants(root: Int): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        val entries = File("/proc").list() ?: return emptyList()
        for (name in entries) {
            val pid = name.toIntOrNull() ?: continue
            val parent = readStat(pid)?.parentPid ?: continue
            children.getOrPut(parent) { mutableListOf() }.add(pid)
        }
        val result = ArrayList<Int>()
        val queue = java.util.ArrayDeque<Int>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val current = queue.pollFirst() ?: break
            for (child in children[current].orEmpty()) {
                result.add(child)
                queue.addLast(child)
            }
        }
        return result
    }

    fun signal(pid: Int, signal: Int) {
        try {
            Os.kill(pid, signal)
        } catch (e: ErrnoException) {
            // Proses sudah tidak ada atau bukan milik kita; tidak ada yang perlu dihentikan.
        }
    }

    private fun readStat(pid: Int): ProcStat? {
        val text = try {
            File("/proc/$pid/stat").readText()
        } catch (e: IOException) {
            return null
        }
        // Nama proses di dalam tanda kurung boleh mengandung spasi; baca dari ')' terakhir.
        val end = text.lastIndexOf(')')
        if (end < 0 || end + 2 >= text.length) return null
        val fields = text.substring(end + 2).trim().split(' ')
        if (fields.size < 2) return null
        val state = fields[0].firstOrNull() ?: return null
        val parent = fields[1].toIntOrNull() ?: return null
        return ProcStat(state, parent)
    }
}
