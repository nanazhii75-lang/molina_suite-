package com.molina.suite.feature.settings.export

import android.content.Context
import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.ConversionResult
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ShellCommand
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import com.molina.suite.feature.settings.packages.PackageDetection
import com.molina.suite.feature.settings.packages.PackageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pelaksana [DocumentConverter] berbasis pandoc (dan Typst untuk PDF) di dalam Debian.
 * Kesiapan alat dibaca dari katalog dan detektor menu Paket, jadi hasilnya sama dengan menu itu,
 * lalu perintahnya dijalankan lewat kontrak [ShellCommandRunner] tanpa mengenal engine terminal.
 * Pemakai (mis. tab Code) hanya mengenal [DocumentConverter].
 *
 * [filesDir] bash milik Molina Terminal ada di usr/bin di bawah folder files aplikasi.
 * Membatalkan coroutine hanya berhenti menunggu hasil; pandoc yang sudah berjalan berhenti
 * sendiri paling lama setelah batas waktu formatnya.
 */
internal class PandocDocumentConverter(
    context: Context,
    private val shell: ShellCommandRunner
) : DocumentConverter {

    private val appContext = context.applicationContext

    override fun targetsFor(extension: String): List<DocumentFormat> {
        val source = DocumentFormat.fromExtension(extension) ?: return emptyList()
        if (!source.canBeSource) return emptyList()
        return DocumentFormat.entries.filter { it != source }
    }

    override suspend fun convert(request: ConversionRequest): ConversionResult {
        val script = PandocCommandBuilder.script(request)
            ?: return ConversionResult.Failure(INVALID_PATH_REASON)

        val missing = missingTools(PandocCommandBuilder.requiredTools(request.target))
        when (missing) {
            is Readiness.NoDebian -> return ConversionResult.NoDebian
            is Readiness.Missing -> return ConversionResult.ToolMissing(missing.toolIds)
            is Readiness.Ready -> Unit
        }

        val command = ShellCommand(
            executable = File(appContext.filesDir, BASH_RELATIVE_PATH).absolutePath,
            arguments = listOf("-c", ScriptRunLine.wrapForDebian(script)),
            label = "Ekspor ${File(request.inputPath).name}"
        )
        return when (val result = shell.runInBackground(command)) {
            is ShellResult.Failed -> ConversionResult.Failure(result.reason)
            is ShellResult.Finished -> interpret(request, result)
        }
    }

    private sealed interface Readiness {
        object Ready : Readiness
        object NoDebian : Readiness
        data class Missing(val toolIds: List<String>) : Readiness
    }

    /** Status terpasang tidak terbaca dianggap siap: biarkan galat asli pandoc yang tampil. */
    private suspend fun missingTools(toolIds: List<String>): Readiness = withContext(Dispatchers.IO) {
        when (val detection = PackageDetector.detect(appContext)) {
            is PackageDetection.NoDebian -> Readiness.NoDebian
            is PackageDetection.Failed -> Readiness.Ready
            is PackageDetection.Ok -> {
                val missing = toolIds.filter { detection.installed[it] == false }
                if (missing.isEmpty()) Readiness.Ready else Readiness.Missing(missing)
            }
        }
    }

    private fun interpret(request: ConversionRequest, result: ShellResult.Finished): ConversionResult {
        if (result.exitCode == 0 && result.stdout.contains(NO_DEBIAN_MARKER)) {
            // Pembungkus Debian hanya mencetak pesan ini (kode keluar 0) bila proot-distro tidak ada.
            return ConversionResult.NoDebian
        }
        return when (result.exitCode) {
            0 -> {
                val output = File(request.outputPath)
                if (output.isFile && output.length() > 0L) {
                    ConversionResult.Success(request.outputPath)
                } else {
                    ConversionResult.Failure("Pandoc selesai tetapi berkas hasil tidak ditemukan: ${request.outputPath}", 0)
                }
            }
            PandocCommandBuilder.TIMEOUT_EXIT_CODE -> ConversionResult.Failure(
                "Waktu habis (${PandocCommandBuilder.timeoutSeconds(request.target)} detik). " +
                    "Dokumen mungkin terlalu besar untuk perangkat ini.",
                result.exitCode
            )
            else -> ConversionResult.Failure(errorSummary(result), result.exitCode)
        }
    }

    /** Beberapa baris terakhir stderr tanpa peringatan proot, dibatasi panjangnya. */
    private fun errorSummary(result: ShellResult.Finished): String {
        val lines = result.stderr.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("proot warning") }
            .toList()
            .takeLast(MAX_ERROR_LINES)
        val text = lines.joinToString("\n").take(MAX_ERROR_CHARS)
        return text.ifEmpty { "Pandoc gagal (kode ${result.exitCode})." }
    }

    private companion object {
        const val BASH_RELATIVE_PATH = "usr/bin/bash"
        const val NO_DEBIAN_MARKER = "Debian belum terpasang"
        const val MAX_ERROR_LINES = 6
        const val MAX_ERROR_CHARS = 600
        const val INVALID_PATH_REASON =
            "Jalur berkas tidak valid. Berkas sumber dan hasil harus ada di penyimpanan bersama " +
                "(/sdcard), dan nama hasil harus berakhir dengan ekstensi format tujuan."
    }
}
