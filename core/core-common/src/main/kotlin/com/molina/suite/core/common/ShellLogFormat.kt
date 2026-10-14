package com.molina.suite.core.common

import java.util.Locale

/** Penyusun teks log perintah shell; murni, tanpa Android, supaya mudah diuji. */
internal object ShellLogFormat {

    const val MAX_LINES = 12
    const val MAX_LINE_CHARS = 300
    private const val UNNAMED = "tanpa label"

    private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    /** Pengenal perintah di log: labelnya satu baris, atau nama berkas executable bila label kosong. */
    fun name(command: ShellCommand): String {
        val label = command.label.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (label.isNotEmpty()) return label.take(MAX_LINE_CHARS)
        return command.executable.substringAfterLast('/').ifEmpty { UNNAMED }
    }

    /** Lama dalam detik dengan satu desimal, mis. "1.2 dtk"; selalu memakai titik desimal. */
    fun seconds(nanos: Long): String =
        String.format(Locale.ROOT, "%.1f dtk", nanos.coerceAtLeast(0L) / 1_000_000_000.0)

    /**
     * Sampai [MAX_LINES] baris terakhir [text] yang tidak kosong, tanpa kode warna ANSI dan
     * carriage return, tiap baris dipotong di [MAX_LINE_CHARS] karakter.
     */
    fun tail(text: String): List<String> =
        text.replace("\r", "\n")
            .lineSequence()
            .map { ANSI.replace(it, "").trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(MAX_LINES)
            .map { if (it.length > MAX_LINE_CHARS) it.take(MAX_LINE_CHARS) + "…" else it }
}
