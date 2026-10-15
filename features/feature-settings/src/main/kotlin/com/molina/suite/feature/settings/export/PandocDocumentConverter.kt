package com.molina.suite.feature.settings.export

import android.content.Context
import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.ConversionResult
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import java.io.File

/**
 * Pelaksana [DocumentConverter] berbasis pandoc (dan Typst untuk PDF sebagai hasil) di dalam
 * Debian. Sumber yang punya [DocumentFormat.restrictedTargets] (PDF, spreadsheet, presentasi)
 * bukan urusannya: pandoc tidak membacanya, jadi itu ditangani [PdfDocumentConverter] dan
 * [PythonMarkdownConverter] lewat [RoutingDocumentConverter]. Kesiapan alat dan penjalanan skrip
 * dikerjakan [ShellConversionRunner]; pemakai (mis. tab Code) hanya mengenal [DocumentConverter].
 */
internal class PandocDocumentConverter(
    context: Context,
    shell: ShellCommandRunner
) : DocumentConverter {

    private val runner = ShellConversionRunner(context, shell)

    override fun targetsFor(extension: String): List<DocumentFormat> {
        val source = DocumentFormat.fromExtension(extension) ?: return emptyList()
        if (!source.canBeSource || source.restrictedTargets != null) return emptyList()
        return DocumentFormat.entries.filter { it != source && it.canBeTarget }
    }

    override suspend fun convert(request: ConversionRequest): ConversionResult {
        val script = PandocCommandBuilder.script(request)
            ?: return ConversionResult.Failure(INVALID_PATH_REASON)

        return runner.run(
            script = script,
            label = "Ekspor ${File(request.inputPath).name}",
            toolIds = PandocCommandBuilder.requiredTools(request.target)
        ) { result -> interpret(request, result) }
    }

    private fun interpret(request: ConversionRequest, result: ShellResult.Finished): ConversionResult =
        when (result.exitCode) {
            0 -> ShellConversionRunner.verifyOutput(request.outputPath, TOOL_NAME)
            PandocCommandBuilder.TIMEOUT_EXIT_CODE -> ConversionResult.Failure(
                "Waktu habis (${PandocCommandBuilder.timeoutSeconds(request.target)} detik). " +
                    "Dokumen mungkin terlalu besar untuk perangkat ini.",
                result.exitCode
            )
            else -> ConversionResult.Failure(
                ShellConversionRunner.errorSummary(result, TOOL_NAME),
                result.exitCode
            )
        }

    private companion object {
        const val TOOL_NAME = "Pandoc"
        const val INVALID_PATH_REASON =
            "Jalur berkas tidak valid. Berkas sumber dan hasil harus ada di penyimpanan bersama " +
                "(/sdcard), dan nama hasil harus berakhir dengan ekstensi format tujuan."
    }
}
