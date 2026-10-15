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

    private const val DEFAULT_TIMEOUT_SECONDS = 180
    private const val PDF_TIMEOUT_SECONDS = 300

    /** Id katalog Paket yang harus terpasang agar [target] bisa dihasilkan. */
    fun requiredTools(target: DocumentFormat): List<String> =
        if (target == DocumentFormat.PDF) listOf(PANDOC_ID, TYPST_ID) else listOf(PANDOC_ID)

    /** Dokumen Word yang berisi gambar ke Markdown/RST: gambarnya diekstrak berdampingan dengan hasil. */
    private fun extractsMedia(request: ConversionRequest): Boolean =
        request.source == DocumentFormat.DOCX &&
            (request.target == DocumentFormat.MARKDOWN || request.target == DocumentFormat.RST)

    /**
     * Nama folder gambar di samping hasil: nama hasil tanpa ekstensi, hanya huruf/angka/titik/
     * garis bawah/minus supaya tautan gambar di Markdown tidak perlu dikutip, plus `_media`.
     */
    fun mediaDirName(outputBase: String): String =
        outputBase.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.').ifEmpty { "dokumen" } + "_media"

    /**
     * Skrip untuk dokumen Word: dijalankan dari folder hasil supaya tautan gambar relatif terhadap
     * berkas hasil (`<nama>_media/media/imageN.png`). Folder gambar yang dibuat oleh konversi yang
     * gagal ikut dibersihkan; folder lama dengan nama sama tidak disentuh.
     */
    private fun mediaScript(
        partial: String,
        outputDir: String,
        output: String,
        input: String,
        media: String,
        options: List<String>,
        request: ConversionRequest
    ): String {
        val d = "$"
        return """
P=${ScriptRunLine.quote(partial)}
M=${ScriptRunLine.quote(media)}
mkdir -p ${ScriptRunLine.quote(outputDir)} || { echo "Tidak bisa membuat folder hasil." >&2; exit 2; }
cd ${ScriptRunLine.quote(outputDir)} || { echo "Tidak bisa membuka folder hasil." >&2; exit 2; }
[ -e "${d}M" ] && K=1
trap 'rm -f "${d}P"; [ -n "${d}K" ] || [ -n "${d}OK" ] || rm -rf "${d}M"' EXIT
timeout ${timeoutSeconds(request.target)} pandoc ${options.joinToString(" ")} --extract-media="${d}M" -o "${d}P" ${ScriptRunLine.quote(input)} && mv -f "${d}P" ${ScriptRunLine.quote(output)} && OK=1
status=${d}?
rmdir "${d}M" 2>/dev/null
exit "${d}status"
""".trimStart()
    }

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
        val input = SharedPaths.resolve(request.inputPath) ?: return null
        val output = SharedPaths.resolve(request.outputPath) ?: return null
        val extension = "." + request.target.extension
        if (!output.endsWith(extension, ignoreCase = true) || output == input) return null

        val inputDir = input.substringBeforeLast('/')
        val inputName = input.substringAfterLast('/')
        val outputDir = output.substringBeforeLast('/')
        val partial = output.dropLast(extension.length) + ".part" + extension
        val reader = request.source.reader ?: return null
        val writer = request.target.writer ?: return null

        val options = ArrayList<String>()
        options.add("-f " + ScriptRunLine.quote(reader))
        // Dari Word ke Markdown: tanpa HTML mentah, supaya gambar tetap berupa ![](...) polos.
        val writerName = if (extractsMedia(request) && request.target == DocumentFormat.MARKDOWN) "$writer-raw_html" else writer
        options.add("-t " + ScriptRunLine.quote(writerName))
        when (request.target) {
            DocumentFormat.HTML -> options.add("-s")
            DocumentFormat.PDF -> options.add("--pdf-engine=typst")
            DocumentFormat.MARKDOWN, DocumentFormat.RST, DocumentFormat.PLAIN -> options.add("--wrap=none")
            else -> Unit
        }
        val argument = ScriptRunLine.quote(if (inputName.startsWith("-")) "./$inputName" else inputName)

        if (extractsMedia(request)) {
            val media = mediaDirName(output.substringAfterLast('/').dropLast(extension.length))
            return mediaScript(partial, outputDir, output, input, media, options, request)
        }
        return "P=" + ScriptRunLine.quote(partial) +
            " && trap 'rm -f \"\$P\"' EXIT" +
            " && mkdir -p " + ScriptRunLine.quote(outputDir) +
            " && cd " + ScriptRunLine.quote(inputDir) +
            " && timeout " + timeoutSeconds(request.target) + " pandoc " + options.joinToString(" ") +
            " -o \"\$P\" " + argument +
            " && mv -f \"\$P\" " + ScriptRunLine.quote(output)
    }
}
