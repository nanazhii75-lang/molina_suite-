package com.molina.suite.feature.settings.export

import android.content.Context
import com.molina.suite.core.common.ConversionResult
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
 * Bagian yang sama bagi semua pelaksana konversi berbasis skrip di Debian: memeriksa alat di
 * katalog Paket, menjalankan skrip lewat kontrak [ShellCommandRunner], lalu menerjemahkan hasil
 * umumnya (Debian belum ada, galat proses, berkas hasil tidak muncul). Penafsiran kode keluar
 * khusus tiap alat tetap di pelaksananya.
 *
 * Membatalkan coroutine hanya berhenti menunggu hasil; skrip yang sudah berjalan berhenti sendiri
 * paling lama setelah batas waktunya.
 */
internal class ShellConversionRunner(
    context: Context,
    private val shell: ShellCommandRunner
) {

    private val appContext = context.applicationContext

    private sealed interface Readiness {
        object Ready : Readiness
        object NoDebian : Readiness
        data class Missing(val toolIds: List<String>) : Readiness
    }

    /**
     * Menjalankan [script] di Debian setelah memastikan [toolIds] terpasang. [interpret] hanya
     * dipanggil bila prosesnya selesai dan Debian ada.
     */
    suspend fun run(
        script: String,
        label: String,
        toolIds: List<String>,
        interpret: (ShellResult.Finished) -> ConversionResult
    ): ConversionResult {
        when (val readiness = missingTools(toolIds)) {
            is Readiness.NoDebian -> return ConversionResult.NoDebian
            is Readiness.Missing -> return ConversionResult.ToolMissing(readiness.toolIds)
            is Readiness.Ready -> Unit
        }
        val command = ShellCommand(
            executable = File(appContext.filesDir, BASH_RELATIVE_PATH).absolutePath,
            arguments = listOf("-c", ScriptRunLine.wrapForDebian(script)),
            label = label
        )
        return when (val result = shell.runInBackground(command)) {
            is ShellResult.Failed -> ConversionResult.Failure(result.reason)
            is ShellResult.Finished -> {
                if (result.exitCode == 0 && result.stdout.contains(NO_DEBIAN_MARKER)) {
                    // Pembungkus Debian hanya mencetak pesan ini (kode keluar 0) bila proot-distro tidak ada.
                    ConversionResult.NoDebian
                } else {
                    interpret(result)
                }
            }
        }
    }

    /** Status terpasang tidak terbaca dianggap siap: biarkan galat asli alatnya yang tampil. */
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

    companion object {
        private const val BASH_RELATIVE_PATH = "usr/bin/bash"
        private const val NO_DEBIAN_MARKER = "Debian belum terpasang"
        private const val MAX_ERROR_LINES = 6
        private const val MAX_ERROR_CHARS = 600

        /** Sukses bila berkas hasil ada dan tidak kosong; [toolName] dipakai untuk pesan galatnya. */
        fun verifyOutput(outputPath: String, toolName: String): ConversionResult {
            val output = File(outputPath)
            return if (output.isFile && output.length() > 0L) {
                ConversionResult.Success(outputPath)
            } else {
                ConversionResult.Failure("$toolName selesai tetapi berkas hasil tidak ditemukan: $outputPath", 0)
            }
        }

        /** Beberapa baris terakhir stderr tanpa peringatan proot, dibatasi panjangnya. */
        fun errorSummary(result: ShellResult.Finished, toolName: String): String {
            val lines = result.stderr.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("proot warning") }
                .toList()
                .takeLast(MAX_ERROR_LINES)
            val text = lines.joinToString("\n").take(MAX_ERROR_CHARS)
            return text.ifEmpty { "$toolName gagal (kode ${result.exitCode})." }
        }
    }
}
