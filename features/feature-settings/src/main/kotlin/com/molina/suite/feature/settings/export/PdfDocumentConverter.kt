package com.molina.suite.feature.settings.export

import android.content.Context
import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.ConversionResult
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Pelaksana [DocumentConverter] untuk sumber PDF (ke Markdown atau teks biasa) di dalam Debian,
 * lewat poppler-utils dan, untuk Markdown, konverter python3 yang dibundel sebagai aset.
 * Kesiapan alat dan penjalanan skrip dikerjakan [ShellConversionRunner]; pemakai hanya mengenal
 * [DocumentConverter].
 */
internal class PdfDocumentConverter(
    context: Context,
    shell: ShellCommandRunner
) : DocumentConverter {

    private val appContext = context.applicationContext
    private val runner = ShellConversionRunner(context, shell)

    /** Isi konverter python3 dari aset; null bila aset tidak terbaca. Dibaca sekali. */
    private val converterSource: String? by lazy { readConverterAsset() }

    override fun targetsFor(extension: String): List<DocumentFormat> =
        if (DocumentFormat.fromExtension(extension) == DocumentFormat.PDF) PdfCommandBuilder.TARGETS else emptyList()

    override suspend fun convert(request: ConversionRequest): ConversionResult {
        if (request.source != DocumentFormat.PDF) {
            return ConversionResult.Failure(NOT_PDF_REASON)
        }
        val source = if (request.target == DocumentFormat.MARKDOWN) {
            withContext(Dispatchers.IO) { converterSource }
                ?: return ConversionResult.Failure(ASSET_MISSING_REASON)
        } else {
            null
        }
        val script = PdfCommandBuilder.script(request, source)
            ?: return ConversionResult.Failure(INVALID_PATH_REASON)

        return runner.run(
            script = script,
            label = "Ubah ${File(request.inputPath).name}",
            toolIds = PdfCommandBuilder.requiredTools(request.target)
        ) { result -> interpret(request, result) }
    }

    private fun interpret(request: ConversionRequest, result: ShellResult.Finished): ConversionResult =
        when (result.exitCode) {
            0 -> ShellConversionRunner.verifyOutput(request.outputPath, TOOL_NAME)
            PdfCommandBuilder.TIMEOUT_EXIT_CODE -> ConversionResult.Failure(
                "Waktu habis (${PdfCommandBuilder.TIMEOUT_SECONDS} detik). " +
                    "PDF mungkin terlalu besar untuk perangkat ini.",
                result.exitCode
            )
            else -> ConversionResult.Failure(
                ShellConversionRunner.errorSummary(result, TOOL_NAME),
                result.exitCode
            )
        }

    private fun readConverterAsset(): String? = try {
        appContext.assets.open(CONVERTER_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val CONVERTER_ASSET = "molina-docs/pdf2md.py"
        const val TOOL_NAME = "Konversi PDF"
        const val NOT_PDF_REASON = "Pelaksana ini hanya menangani sumber PDF."
        const val ASSET_MISSING_REASON =
            "Konverter PDF tidak ditemukan di dalam aplikasi. Pasang ulang aplikasi."
        const val INVALID_PATH_REASON =
            "Jalur berkas tidak valid. Berkas sumber dan hasil harus ada di penyimpanan bersama " +
                "(/sdcard), dan nama hasil harus berakhir dengan ekstensi format tujuan."
    }
}
