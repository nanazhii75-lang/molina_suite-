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

/**
 * Pelaksana [DocumentConverter] untuk sumber XLSX (ke Markdown) di dalam Debian, lewat konverter
 * python3 yang dibundel sebagai aset. Kesiapan alat dan penjalanan skrip dikerjakan
 * [ShellConversionRunner]; pemakai hanya mengenal [DocumentConverter].
 */
internal class XlsxDocumentConverter(
    context: Context,
    shell: ShellCommandRunner
) : DocumentConverter {

    private val runner = ShellConversionRunner(context, shell)
    private val converterAsset = AssetText(context, CONVERTER_ASSET)

    override fun targetsFor(extension: String): List<DocumentFormat> =
        if (DocumentFormat.fromExtension(extension) == DocumentFormat.XLSX) XlsxCommandBuilder.TARGETS else emptyList()

    override suspend fun convert(request: ConversionRequest): ConversionResult {
        if (request.source != DocumentFormat.XLSX) {
            return ConversionResult.Failure(NOT_XLSX_REASON)
        }
        val source = withContext(Dispatchers.IO) { converterAsset.text }
            ?: return ConversionResult.Failure(ASSET_MISSING_REASON)
        val script = XlsxCommandBuilder.script(request, source)
            ?: return ConversionResult.Failure(INVALID_PATH_REASON)

        return runner.run(
            script = script,
            label = "Ubah ${File(request.inputPath).name}",
            toolIds = XlsxCommandBuilder.requiredTools()
        ) { result -> interpret(request, result) }
    }

    private fun interpret(request: ConversionRequest, result: ShellResult.Finished): ConversionResult =
        when (result.exitCode) {
            0 -> ShellConversionRunner.verifyOutput(request.outputPath, TOOL_NAME)
            XlsxCommandBuilder.TIMEOUT_EXIT_CODE -> ConversionResult.Failure(
                "Waktu habis (${XlsxCommandBuilder.TIMEOUT_SECONDS} detik). " +
                    "Berkas mungkin terlalu besar untuk perangkat ini.",
                result.exitCode
            )
            else -> ConversionResult.Failure(
                ShellConversionRunner.errorSummary(result, TOOL_NAME),
                result.exitCode
            )
        }

    private companion object {
        const val CONVERTER_ASSET = "molina-docs/xlsx2md.py"
        const val TOOL_NAME = "Konversi XLSX"
        const val NOT_XLSX_REASON = "Pelaksana ini hanya menangani sumber XLSX."
        const val ASSET_MISSING_REASON =
            "Konverter XLSX tidak ditemukan di dalam aplikasi. Pasang ulang aplikasi."
        const val INVALID_PATH_REASON =
            "Jalur berkas tidak valid. Berkas sumber dan hasil harus ada di penyimpanan bersama " +
                "(/sdcard), dan nama hasil harus berakhir dengan .md."
    }
}
