package com.molina.suite.feature.settings.logcat

/**
 * Membaca keluaran "logcat -v threadtime" per baris. Baris pembuka buffer ("--------- beginning of
 * main") dan baris yang tidak berformat threadtime tidak menghasilkan entri; pemanggil boleh
 * mengabaikannya. Seluruh logika format ada di sini supaya tampilan tidak mengenal format logcat.
 */
internal object LogcatParser {

    private val THREADTIME = Regex(
        """^(\d\d-\d\d)\s+(\d\d:\d\d:\d\d\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFAS])\s+(.*?)\s*:\s?(.*)$"""
    )

    /** Entri dari [line], atau null bila bukan baris threadtime yang valid. */
    fun parse(line: String): LogcatEntry? {
        val match = THREADTIME.find(line) ?: return null
        val groups = match.groupValues
        val level = LogcatLevel.fromSymbol(groups[5][0]) ?: return null
        val pid = groups[3].toIntOrNull() ?: return null
        val tid = groups[4].toIntOrNull() ?: return null
        return LogcatEntry(groups[1], groups[2], pid, tid, level, groups[6], groups[7])
    }
}
