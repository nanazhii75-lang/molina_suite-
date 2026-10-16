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
    const val TIMEOUT_EXIT_CODE = ScriptFragments.TIMEOUT_EXIT_CODE

    /** Format yang bisa dihasilkan dari PDF. */
    val TARGETS: List<DocumentFormat> = listOf(DocumentFormat.MARKDOWN, DocumentFormat.PLAIN)

    /** Batas waktu total terpanjang (ekstraksi + penyusunan dengan OCR), dalam detik. */
    const val TIMEOUT_SECONDS = 740

    private const val EXTRACT_TIMEOUT_SECONDS = 200
    private const val CONVERT_TIMEOUT_SECONDS = 100

    /** Penyusunan dengan OCR; konverter membatasi dirinya sendiri (420 detik) sebelum ini tercapai. */
    private const val OCR_CONVERT_TIMEOUT_SECONDS = 540
    private const val D = ScriptFragments.D

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
        return ScriptFragments.header(partial, outputDir, inputDir) + body + ScriptFragments.footer(output)
    }

    private fun markdownSteps(argument: String, converterSource: String, request: ConversionRequest): String {
        val flags = ArrayList<String>()
        if (!request.options.stripRepeatedHeaders) flags.add("--keep-headers")
        if (request.options.pageBreaks) flags.add("--page-breaks")
        val flagText = if (flags.isEmpty()) "" else flags.joinToString(" ") + " "
        // OCR otomatis bila tesseract terpasang. Tanpanya skrip sama seperti sebelumnya: `-i` mengabaikan
        // gambar. Dengan OCR gambar ditulis ke folder sementara W supaya konverter bisa membacanya.
        // `-hidden`: PDF hasil scan yang sudah punya lapisan teks tak terlihat tetap terbaca.
        return """
W=${D}(mktemp -d "${D}{TMPDIR:-/tmp}/molina-pdf.XXXXXX") || fail "Tidak bisa membuat folder sementara."
trap 'rm -rf "${D}P" "${D}W"' EXIT
X="${D}W/p.xml"
OCR=
command -v tesseract >/dev/null 2>&1 && OCR=1
if [ -n "${D}OCR" ]; then
  timeout $EXTRACT_TIMEOUT_SECONDS pdftohtml -xml -hidden -q -fontfullname -enc UTF-8 $argument "${D}W/p"
else
  timeout $EXTRACT_TIMEOUT_SECONDS pdftohtml -xml -i -hidden -q -fontfullname -enc UTF-8 $argument "${D}W/p"
fi
status=${D}?
[ "${D}status" -eq $TIMEOUT_EXIT_CODE ] && exit $TIMEOUT_EXIT_CODE
[ "${D}status" -eq 0 ] || fail "pdftohtml gagal membaca PDF (terenkripsi atau rusak?)."
CT=$CONVERT_TIMEOUT_SECONDS
[ -n "${D}OCR" ] && CT=$OCR_CONVERT_TIMEOUT_SECONDS
timeout "${D}CT" python3 -c ${ScriptRunLine.quote(converterSource)} $flagText${D}{OCR:+--ocr }"${D}X" > "${D}P"
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
}
