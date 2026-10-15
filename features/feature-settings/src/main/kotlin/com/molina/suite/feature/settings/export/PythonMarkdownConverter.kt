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
 * Pelaksana [DocumentConverter] untuk sumber yang dibaca konverter python3 ke Markdown di dalam
 * Debian (spreadsheet dan presentasi, lihat [PythonMarkdownSpec]). Kesiapan alat dan penjalanan
 * skrip dikerjakan [ShellConversionRunner]; pemakai hanya mengenal [DocumentConverter].
 */
internal class PythonMarkdownConverter(
    context: Context,
    shell: ShellCommandRunner,
    private val spec: PythonMarkdownSpec
) : DocumentConverter {

    private val runner = ShellConversionRunner(context, shell)
    private val converterAsset = AssetText(context, spec.asset)

    override fun targetsFor(extension: String): List<DocumentFormat> =
        if (DocumentFormat.fromExtension(extension) in spec.sources) PythonMarkdownCommandBuilder.TARGETS else emptyList()

    override suspend fun convert(request: ConversionRequest): ConversionResult {
        if (request.source !in spec.sources) {
            return ConversionResult.Failure("Pelaksana ini tidak menangani sumber ${request.source.name}.")
        }
        val source = withContext(Dispatchers.IO) { converterAsset.text }
            ?: return ConversionResult.Failure(ASSET_MISSING_REASON)
        val script = PythonMarkdownCommandBuilder.script(request, spec, source)
            ?: return ConversionResult.Failure(INVALID_PATH_REASON)

        return runner.run(
            script = script,
            label = "Ubah ${File(request.inputPath).name}",
            toolIds = PythonMarkdownCommandBuilder.requiredTools()
        ) { result -> interpret(request, result) }
    }

    private fun interpret(request: ConversionRequest, result: ShellResult.Finished): ConversionResult =
        when (result.exitCode) {
            0 -> ShellConversionRunner.verifyOutput(request.outputPath, spec.toolName)
            PythonMarkdownCommandBuilder.TIMEOUT_EXIT_CODE -> ConversionResult.Failure(
                "Waktu habis (${spec.timeoutSeconds} detik). ${spec.timeoutHint}",
                result.exitCode
            )
            else -> ConversionResult.Failure(
                ShellConversionRunner.errorSummary(result, spec.toolName),
                result.exitCode
            )
        }

    private companion object {
        const val ASSET_MISSING_REASON =
            "Konverter tidak ditemukan di dalam aplikasi. Pasang ulang aplikasi."
        const val INVALID_PATH_REASON =
            "Jalur berkas tidak valid. Berkas sumber dan hasil harus ada di penyimpanan bersama " +
                "(/sdcard), dan nama hasil harus berakhir dengan .md."
    }
}
