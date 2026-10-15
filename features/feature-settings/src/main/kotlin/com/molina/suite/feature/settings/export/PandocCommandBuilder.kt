package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ScriptRunLine

/**
 * Menyusun skrip bash (satu baris) yang menjalankan pandoc di dalam Debian untuk sebuah
 * [ConversionRequest]. Hanya menyusun teks; tidak menjalankan apa pun, jadi bisa diuji tanpa
 * perangkat. Id alat mengikuti katalog menu Paket.
 */
internal object PandocCommandBuilder {

    const val PANDOC_ID = "pandoc"
    const val TYPST_ID = "typst"

    /** Kode keluar `timeout` bila perintah dihentikan karena melewati batas waktu. */
    const val TIMEOUT_EXIT_CODE = 124

    private const val SHARED_PREFIX = "/sdcard/"
    private const val DEFAULT_TIMEOUT_SECONDS = 180
    private const val PDF_TIMEOUT_SECONDS = 300

    /** Id katalog Paket yang harus terpasang agar [target] bisa dihasilkan. */
    fun requiredTools(target: DocumentFormat): List<String> =
        if (target == DocumentFormat.PDF) listOf(PANDOC_ID, TYPST_ID) else listOf(PANDOC_ID)

    fun timeoutSeconds(target: DocumentFormat): Int =
        if (target == DocumentFormat.PDF) PDF_TIMEOUT_SECONDS else DEFAULT_TIMEOUT_SECONDS

    /**
     * Skrip untuk [request], atau null bila jalurnya tidak aman: harus absolut di penyimpanan
     * bersama (satu-satunya yang terlihat dari Debian), tanpa baris baru atau segmen "..", dan
     * nama berkas hasil harus berakhir dengan ekstensi format targetnya.
     *
     * Hasil ditulis ke berkas sementara lalu dipindah, supaya konversi yang gagal atau dihentikan
     * tidak meninggalkan berkas hasil yang rusak atau menimpa berkas lama. Perintah dijalankan
     * dari folder berkas sumber supaya gambar dan tautan relatif ikut terbaca.
     */
    fun script(request: ConversionRequest): String? {
        val input = sharedPath(request.inputPath) ?: return null
        val output = sharedPath(request.outputPath) ?: return null
        val extension = "." + request.target.extension
        if (!output.endsWith(extension, ignoreCase = true) || output == input) return null

        val inputDir = input.substringBeforeLast('/')
        val inputName = input.substringAfterLast('/')
        val outputDir = output.substringBeforeLast('/')
        val partial = output.dropLast(extension.length) + ".part" + extension
        val reader = request.source.reader ?: return null

        val options = ArrayList<String>()
        options.add("-f " + ScriptRunLine.quote(reader))
        options.add("-t " + ScriptRunLine.quote(request.target.writer))
        when (request.target) {
            DocumentFormat.HTML -> options.add("-s")
            DocumentFormat.PDF -> options.add("--pdf-engine=typst")
            DocumentFormat.MARKDOWN, DocumentFormat.RST, DocumentFormat.PLAIN -> options.add("--wrap=none")
            else -> Unit
        }
        val argument = ScriptRunLine.quote(if (inputName.startsWith("-")) "./$inputName" else inputName)

        return "P=" + ScriptRunLine.quote(partial) +
            " && trap 'rm -f \"\$P\"' EXIT" +
            " && mkdir -p " + ScriptRunLine.quote(outputDir) +
            " && cd " + ScriptRunLine.quote(inputDir) +
            " && timeout " + timeoutSeconds(request.target) + " pandoc " + options.joinToString(" ") +
            " -o \"\$P\" " + argument +
            " && mv -f \"\$P\" " + ScriptRunLine.quote(output)
    }

    /** Jalur di penyimpanan bersama dalam bentuk yang terlihat dari Debian, atau null bila tidak valid. */
    private fun sharedPath(path: String): String? {
        if (path.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        val normalized = ScriptRunLine.normalizePath(path)
        if (!normalized.startsWith(SHARED_PREFIX) || normalized.endsWith("/")) return null
        if (normalized.split('/').any { it == ".." }) return null
        return normalized
    }
}
