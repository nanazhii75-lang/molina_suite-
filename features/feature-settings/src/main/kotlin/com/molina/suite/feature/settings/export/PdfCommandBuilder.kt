package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ScriptRunLine

/**
 * Menyusun skrip bash yang mengubah PDF menjadi Markdown atau teks biasa di dalam Debian.
 * Hanya menyusun teks; tidak menjalankan apa pun, jadi bisa diuji tanpa perangkat.
 *
 * Markdown: `pdftohtml -xml` (poppler-utils) menghasilkan teks beserta ukuran font dan posisinya,
 * lalu konverter python3 ([converterSource], aset `molina-docs/pdf2md.py`) menyusun heading, daftar,
 * tabel, dan paragraf. Teks biasa: `pdftotext`. Id alat mengikuti katalog menu Paket.
 *
 * Kode keluar skrip: 0 berhasil, 124 waktu habis, 3 PDF tanpa teks (hasil scan), lainnya galat
 * dengan pesan berbahasa Indonesia di stderr.
 */
internal object PdfCommandBuilder {

    const val POPPLER_ID = "poppler"
    const val PYTHON_ID = "python"

    /** Kode keluar `timeout` bila perintah dihentikan karena melewati batas waktu. */
    const val TIMEOUT_EXIT_CODE = 124

    /** Format yang bisa dihasilkan dari PDF. */
    val TARGETS: List<DocumentFormat> = listOf(DocumentFormat.MARKDOWN, DocumentFormat.PLAIN)

    /** Batas waktu total gabungan ekstraksi dan penyusunan, dalam detik. */
    const val TIMEOUT_SECONDS = 300

    private const val EXTRACT_TIMEOUT_SECONDS = 200
    private const val CONVERT_TIMEOUT_SECONDS = 100
    /** Tanda dolar untuk dipakai di dalam templat skrip Kotlin tanpa dianggap templat string. */
    private const val D = "$"

    /** Id katalog Paket yang harus terpasang agar [target] bisa dihasilkan dari PDF. */
    fun requiredTools(target: DocumentFormat): List<String> =
        if (target == DocumentFormat.MARKDOWN) listOf(POPPLER_ID, PYTHON_ID) else listOf(POPPLER_ID)

    /**
     * Skrip untuk [request], atau null bila jalurnya tidak aman (harus absolut di penyimpanan
     * bersama, nama hasil berakhir dengan ekstensi targetnya), targetnya bukan Markdown/teks, atau
     * [converterSource] kosong padahal targetnya Markdown.
     *
     * Hasil ditulis ke berkas sementara lalu dipindah, supaya konversi yang gagal atau dihentikan
     * tidak meninggalkan berkas rusak atau menimpa berkas lama.
     */
    fun script(request: ConversionRequest, converterSource: String?): String? {
        if (request.source != DocumentFormat.PDF || request.target !in TARGETS) return null
        val input = SharedPaths.resolve(request.inputPath) ?: return null
        val output = SharedPaths.resolve(request.outputPath) ?: return null
        val extension = "." + request.target.extension
        if (!output.endsWith(extension, ignoreCase = true) || output == input) return null

        val inputDir = input.substringBeforeLast('/')
        val inputName = input.substringAfterLast('/')
        val outputDir = output.substringBeforeLast('/')
        val partial = output.dropLast(extension.length) + ".part" + extension
        val argument = ScriptRunLine.quote(if (inputName.startsWith("-")) "./$inputName" else inputName)

        val body = when (request.target) {
            DocumentFormat.MARKDOWN -> {
                if (converterSource.isNullOrBlank()) return null
                markdownSteps(argument, converterSource, request)
            }
            else -> plainSteps(argument, request)
        }
        return header(partial, outputDir, inputDir) + body + footer(output)
    }

    private fun header(partial: String, outputDir: String, inputDir: String): String = """
P=${ScriptRunLine.quote(partial)}
X=
trap 'rm -f "${D}P" "${D}X"' EXIT
fail() { echo "${D}1" >&2; exit 2; }
mkdir -p ${ScriptRunLine.quote(outputDir)} || fail "Tidak bisa membuat folder hasil."
cd ${ScriptRunLine.quote(inputDir)} || fail "Tidak bisa membuka folder sumber."
""".trimStart()

    private fun markdownSteps(argument: String, converterSource: String, request: ConversionRequest): String {
        val flags = ArrayList<String>()
        if (!request.options.stripRepeatedHeaders) flags.add("--keep-headers")
        if (request.options.pageBreaks) flags.add("--page-breaks")
        val flagText = if (flags.isEmpty()) "" else flags.joinToString(" ") + " "
        return """
X=${D}(mktemp "${D}{TMPDIR:-/tmp}/molina-pdf.XXXXXX") || fail "Tidak bisa membuat berkas sementara."
timeout $EXTRACT_TIMEOUT_SECONDS pdftohtml -xml -i -q -fontfullname -enc UTF-8 -stdout $argument > "${D}X"
status=${D}?
[ "${D}status" -eq $TIMEOUT_EXIT_CODE ] && exit $TIMEOUT_EXIT_CODE
[ "${D}status" -eq 0 ] || fail "pdftohtml gagal membaca PDF (terenkripsi atau rusak?)."
timeout $CONVERT_TIMEOUT_SECONDS python3 -c ${ScriptRunLine.quote(converterSource)} $flagText"${D}X" > "${D}P"
status=${D}?
[ "${D}status" -eq 0 ] || exit "${D}status"
""".trimStart()
    }

    private fun plainSteps(argument: String, request: ConversionRequest): String {
        val extract = "timeout $EXTRACT_TIMEOUT_SECONDS pdftotext -enc UTF-8 $argument -"
        val pipeline = if (request.options.pageBreaks) "$extract > \"${D}P\"" else "$extract | tr -d '\\f' > \"${D}P\""
        return """
set -o pipefail
$pipeline
status=${D}?
[ "${D}status" -eq $TIMEOUT_EXIT_CODE ] && exit $TIMEOUT_EXIT_CODE
[ "${D}status" -eq 0 ] || fail "pdftotext gagal membaca PDF (terenkripsi atau rusak?)."
grep -q '[^[:space:]]' "${D}P" || { echo "PDF tidak memuat teks (mungkin hasil scan; butuh OCR)." >&2; exit 3; }
""".trimStart()
    }

    private fun footer(output: String): String = """
[ -s "${D}P" ] || fail "Hasil kosong."
mv -f "${D}P" ${ScriptRunLine.quote(output)}
""".trimStart()
}
