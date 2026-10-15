package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ScriptRunLine

/**
 * Ciri satu keluarga konverter python3 ke Markdown: format sumber yang ditangani, aset skripnya,
 * dan batas waktunya. Konverternya hanya memakai pustaka standar Python, jadi satu-satunya alat
 * yang dibutuhkan adalah paket `python` di menu Paket.
 */
internal class PythonMarkdownSpec(
    val sources: Set<DocumentFormat>,
    val asset: String,
    val toolName: String,
    val timeoutSeconds: Int,
    val timeoutHint: String
) {
    companion object {
        /** XLSX dan ODS, dibaca `sheet2md.py` yang mengenali jenisnya dari isi berkas. */
        val SPREADSHEET = PythonMarkdownSpec(
            sources = setOf(DocumentFormat.XLSX, DocumentFormat.ODS),
            asset = "molina-docs/sheet2md.py",
            toolName = "Konversi spreadsheet",
            timeoutSeconds = 120,
            timeoutHint = "Berkas mungkin terlalu besar untuk perangkat ini."
        )

        /** PPTX, dibaca `pptx2md.py`. */
        val PRESENTATION = PythonMarkdownSpec(
            sources = setOf(DocumentFormat.PPTX),
            asset = "molina-docs/pptx2md.py",
            toolName = "Konversi presentasi",
            timeoutSeconds = 120,
            timeoutHint = "Presentasi mungkin terlalu besar untuk perangkat ini."
        )
    }
}

/**
 * Menyusun skrip bash yang mengubah satu berkas (spreadsheet atau presentasi) menjadi Markdown di
 * dalam Debian dengan konverter python3 ([converterSource]). Hanya menyusun teks; tidak
 * menjalankan apa pun, jadi bisa diuji tanpa perangkat.
 *
 * Kode keluar skrip: 0 berhasil, 124 waktu habis, 3 tidak ada isi, 4 bukan berkas yang valid
 * (termasuk berkata sandi), lainnya galat dengan pesan berbahasa Indonesia di stderr.
 */
internal object PythonMarkdownCommandBuilder {

    const val PYTHON_ID = "python"
    const val TIMEOUT_EXIT_CODE = ScriptFragments.TIMEOUT_EXIT_CODE

    private const val D = ScriptFragments.D

    /** Format yang bisa dihasilkan; selalu Markdown. */
    val TARGETS: List<DocumentFormat> = listOf(DocumentFormat.MARKDOWN)

    /** Id katalog Paket yang harus terpasang. */
    fun requiredTools(): List<String> = listOf(PYTHON_ID)

    /**
     * Skrip untuk [request], atau null bila jalurnya tidak aman (harus absolut di penyimpanan
     * bersama, nama hasil berakhir `.md`), sumbernya bukan milik [spec], targetnya bukan Markdown,
     * atau [converterSource] kosong.
     */
    fun script(request: ConversionRequest, spec: PythonMarkdownSpec, converterSource: String?): String? {
        if (request.source !in spec.sources || request.target != DocumentFormat.MARKDOWN) return null
        if (converterSource.isNullOrBlank()) return null
        val input = SharedPaths.resolve(request.inputPath) ?: return null
        val output = SharedPaths.resolve(request.outputPath) ?: return null
        val extension = "." + request.target.extension
        if (!output.endsWith(extension, ignoreCase = true) || output == input) return null

        val inputName = input.substringAfterLast('/')
        val partial = output.dropLast(extension.length) + ".part" + extension
        val argument = ScriptRunLine.quote(if (inputName.startsWith("-")) "./$inputName" else inputName)

        val body = """
timeout ${spec.timeoutSeconds} python3 -c ${ScriptRunLine.quote(converterSource)} $argument > "${D}P"
status=${D}?
[ "${D}status" -eq $TIMEOUT_EXIT_CODE ] && exit $TIMEOUT_EXIT_CODE
[ "${D}status" -eq 0 ] || exit "${D}status"
""".trimStart()
        return ScriptFragments.header(partial, output.substringBeforeLast('/'), input.substringBeforeLast('/')) +
            body + ScriptFragments.footer(output)
    }
}
