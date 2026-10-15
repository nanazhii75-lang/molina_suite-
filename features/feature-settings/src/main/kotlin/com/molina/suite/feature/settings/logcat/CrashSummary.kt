package com.molina.suite.feature.settings.logcat

/** Jenis laporan di folder crash. */
internal enum class CrashKind { CRASH, PROCESS_DEATH }

/**
 * Mengambil ringkasan dari teks laporan ([CrashReport] atau [ProcessDeathReport]) untuk notifikasi:
 * jenisnya, baris utama (pesan exception atau peristiwa), dan cuplikan awal bagian utamanya.
 */
internal object CrashSummary {

    private const val STACK_HEADER = "== Stack trace =="
    private const val EVENT_HEADER = "== Peristiwa =="
    private const val SECTION_PREFIX = "== "
    private const val HEADLINE_MAX = 160

    fun kind(report: String): CrashKind {
        val first = report.lineSequence().firstOrNull().orEmpty()
        return if (first.contains("process-death")) CrashKind.PROCESS_DEATH else CrashKind.CRASH
    }

    /** Baris-baris tak kosong dari bagian utama laporan (stack trace atau peristiwa). */
    private fun body(report: String): List<String> {
        val lines = report.lines()
        val start = lines.indexOfFirst { it.startsWith(STACK_HEADER) || it.startsWith(EVENT_HEADER) }
        val section = if (start >= 0) lines.drop(start + 1) else lines
        return section
            .takeWhile { !it.startsWith(SECTION_PREFIX) }
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
    }

    /** Satu baris ringkas, mis. "java.lang.NullPointerException: ..."; kosong bila tidak ada. */
    fun headline(report: String): String = body(report).firstOrNull()?.trim().orEmpty().take(HEADLINE_MAX)

    /** Cuplikan awal bagian utama, paling banyak [maxLines] baris dan [maxChars] karakter. */
    fun preview(report: String, maxLines: Int, maxChars: Int): String =
        body(report).take(maxLines).joinToString("\n").take(maxChars)
}
