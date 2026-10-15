package com.molina.suite.core.common

/**
 * Format dokumen yang dikenal kontrak ekspor.
 *
 * [reader] dan [writer] adalah nama format di konverter; [reader] null berarti
 * format ini hanya bisa menjadi hasil, bukan sumber.
 */
enum class DocumentFormat(
    val extension: String,
    val reader: String?,
    val writer: String
) {
    MARKDOWN("md", "gfm", "gfm"),
    HTML("html", "html", "html"),
    DOCX("docx", "docx", "docx"),
    EPUB("epub", "epub", "epub"),
    RST("rst", "rst", "rst"),
    PDF("pdf", null, "pdf"),
    PLAIN("txt", null, "plain");

    val canBeSource: Boolean get() = reader != null

    companion object {
        private val aliases = mapOf("markdown" to MARKDOWN, "htm" to HTML)

        /** Mencari format dari ekstensi berkas (tanpa titik, huruf besar/kecil diabaikan). */
        fun fromExtension(extension: String): DocumentFormat? {
            val key = extension.trim().removePrefix(".").lowercase()
            return entries.firstOrNull { it.extension == key } ?: aliases[key]
        }
    }
}

/**
 * Permintaan konversi. Path harus absolut; sumber harus format yang bisa dibaca
 * dan target tidak boleh sama dengan sumber.
 */
data class ConversionRequest(
    val inputPath: String,
    val outputPath: String,
    val source: DocumentFormat,
    val target: DocumentFormat
) {
    init {
        require(inputPath.startsWith("/")) { "inputPath harus absolut: $inputPath" }
        require(outputPath.startsWith("/")) { "outputPath harus absolut: $outputPath" }
        require(source.canBeSource) { "Format ${source.name} tidak bisa dijadikan sumber" }
        require(source != target) { "Sumber dan target sama: ${source.name}" }
    }
}

/** Hasil akhir sebuah [ConversionRequest]. */
sealed interface ConversionResult {
    /** Berkas hasil sudah ditulis di [outputPath]. */
    data class Success(val outputPath: String) : ConversionResult

    /** Alat di [toolIds] (id katalog Paket) belum terpasang; arahkan pengguna ke menu Paket. */
    data class ToolMissing(val toolIds: List<String>) : ConversionResult

    /** Debian belum terpasang, jadi belum ada alat yang bisa dipakai; arahkan pengguna ke menu Debian. */
    object NoDebian : ConversionResult

    /** Konversi gagal. [exitCode] null bila proses tidak sempat melapor. */
    data class Failure(val reason: String, val exitCode: Int? = null) : ConversionResult
}

/**
 * Kontrak antara modul pemakai (mis. Code) dan pelaksana konversi dokumen,
 * tanpa mengenal alat atau engine di baliknya.
 */
interface DocumentConverter {
    /** Format target yang ditawarkan untuk berkas berekstensi [extension]; kosong bila tidak didukung. */
    fun targetsFor(extension: String): List<DocumentFormat>

    /** Menjalankan [request] dan menunggu sampai selesai. Panggil dari coroutine sendiri. */
    suspend fun convert(request: ConversionRequest): ConversionResult
}
