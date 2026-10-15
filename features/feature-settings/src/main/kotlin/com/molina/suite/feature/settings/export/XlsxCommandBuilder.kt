package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ScriptRunLine

/**
 * Menyusun skrip bash yang mengubah XLSX menjadi Markdown di dalam Debian dengan konverter python3
 * ([converterSource], aset `molina-docs/xlsx2md.py`, hanya pustaka standar Python). Hanya
 * menyusun teks; tidak menjalankan apa pun, jadi bisa diuji tanpa perangkat.
 *
 * Kode keluar skrip: 0 berhasil, 124 waktu habis, 3 tidak ada data, 4 bukan xlsx yang valid
 * (termasuk berkata sandi), lainnya galat dengan pesan berbahasa Indonesia di stderr.
 */
internal object XlsxCommandBuilder {

    const val PYTHON_ID = "python"
    const val TIMEOUT_EXIT_CODE = ScriptFragments.TIMEOUT_EXIT_CODE
    const val TIMEOUT_SECONDS = 120

    private const val D = ScriptFragments.D

    /** Format yang bisa dihasilkan dari XLSX. */
    val TARGETS: List<DocumentFormat> = listOf(DocumentFormat.MARKDOWN)

    /** Id katalog Paket yang harus terpasang. */
    fun requiredTools(): List<String> = listOf(PYTHON_ID)

    /**
     * Skrip untuk [request], atau null bila jalurnya tidak aman (harus absolut di penyimpanan
     * bersama, nama hasil berakhir `.md`), pasangan formatnya bukan XLSX ke Markdown, atau
     * [converterSource] kosong.
     */
    fun script(request: ConversionRequest, converterSource: String?): String? {
        if (request.source != DocumentFormat.XLSX || request.target != DocumentFormat.MARKDOWN) return null
        if (converterSource.isNullOrBlank()) return null
        val input = SharedPaths.resolve(request.inputPath) ?: return null
        val output = SharedPaths.resolve(request.outputPath) ?: return null
        val extension = "." + request.target.extension
        if (!output.endsWith(extension, ignoreCase = true) || output == input) return null

        val inputName = input.substringAfterLast('/')
        val partial = output.dropLast(extension.length) + ".part" + extension
        val argument = ScriptRunLine.quote(if (inputName.startsWith("-")) "./$inputName" else inputName)

        val body = """
timeout $TIMEOUT_SECONDS python3 -c ${ScriptRunLine.quote(converterSource)} $argument > "${D}P"
status=${D}?
[ "${D}status" -eq $TIMEOUT_EXIT_CODE ] && exit $TIMEOUT_EXIT_CODE
[ "${D}status" -eq 0 ] || exit "${D}status"
""".trimStart()
        return ScriptFragments.header(partial, output.substringBeforeLast('/'), input.substringBeforeLast('/')) +
            body + ScriptFragments.footer(output)
    }
}
