package com.molina.suite.feature.settings.x11

import com.molina.suite.core.common.X11Contract

/** Satu proses di /proc. [state] adalah huruf status (R, S, Z, ...). */
internal data class ProcEntry(
    val pid: Int,
    val ppid: Int,
    val sid: Int,
    val state: Char,
    val comm: String,
    val args: List<String>
) {
    /** Zombie dan proses yang sudah mati tidak lagi memakai memori. */
    val isAlive: Boolean get() = state != 'Z' && state != 'X'

    val commandLine: String get() = args.joinToString(" ")
}

internal data class StatFields(val state: Char, val ppid: Int, val sid: Int)

internal object ProcParser {
    private const val RSS_PREFIX = "VmRSS:"

    /**
     * Membaca /proc/<pid>/stat. Nama proses boleh memuat spasi dan kurung, jadi bidang dibaca
     * setelah tanda ')' terakhir: status, ppid, pgrp, sesi.
     */
    fun parseStat(stat: String): StatFields? {
        val close = stat.lastIndexOf(')')
        if (close < 0) return null
        val fields = stat.substring(close + 1).trim().split(' ')
        if (fields.size < 4) return null
        val state = fields[0].firstOrNull() ?: return null
        val ppid = fields[1].toIntOrNull() ?: return null
        val sid = fields[3].toIntOrNull() ?: return null
        return StatFields(state, ppid, sid)
    }

    /** Memori fisik proses (VmRSS) dalam KB dari isi /proc/<pid>/status; 0 bila tidak ada. */
    fun parseRssKb(status: String): Long {
        for (line in status.lineSequence()) {
            if (!line.startsWith(RSS_PREFIX)) continue
            val digits = line.substring(RSS_PREFIX.length).trim().takeWhile { it.isDigit() }
            return digits.toLongOrNull() ?: 0L
        }
        return 0L
    }
}

/** Memilih proses milik desktop dan server X11 dari satu potret /proc. */
internal object X11Selection {
    private val DESKTOP_NAMES = setOf("openbox", "openbox-session", "pcmanfm", "dbus-launch")
    private const val SESSION_BUS = "dbus-daemon"
    private const val SESSION_FLAG = "--session"

    /**
     * Desktop = proses akar dari berkas pid (bila masih berupa Openbox/dbus-launch) beserta
     * seluruh keturunannya, anggota sesinya bila akar adalah pemimpin sesi, dan sisa proses
     * desktop yang dikenali lewat nama (Openbox, pcmanfm, dbus-launch, dbus-daemon --session).
     */
    fun desktop(table: List<ProcEntry>, rootPid: Int?, selfPid: Int): Set<Int> {
        val live = table.filter { it.isAlive && it.pid != selfPid }
        val keep = HashSet<Int>()
        val root = rootPid?.let { id -> live.firstOrNull { it.pid == id } }
        if (root != null && looksLikeDesktopRoot(root)) {
            keep += root.pid
            if (root.sid == root.pid) {
                for (entry in live) if (entry.sid == root.pid) keep += entry.pid
            }
            var grew = true
            while (grew) {
                grew = false
                for (entry in live) {
                    if (entry.pid !in keep && entry.ppid in keep) {
                        keep += entry.pid
                        grew = true
                    }
                }
            }
        }
        for (entry in live) if (isLeftover(entry)) keep += entry.pid
        return keep
    }

    /** Proses X server lorie (app_process dengan kelas titik masuknya). */
    fun server(table: List<ProcEntry>, selfPid: Int): Set<Int> =
        table.filter {
            it.isAlive && it.pid != selfPid && it.args.contains(X11Contract.SERVER_ENTRY_POINT)
        }.mapTo(HashSet()) { it.pid }

    private fun looksLikeDesktopRoot(entry: ProcEntry): Boolean {
        val line = entry.commandLine
        return line.contains("openbox") || line.contains("dbus-launch")
    }

    private fun isLeftover(entry: ProcEntry): Boolean =
        entry.comm in DESKTOP_NAMES ||
            (entry.comm == SESSION_BUS && entry.args.contains(SESSION_FLAG))
}
