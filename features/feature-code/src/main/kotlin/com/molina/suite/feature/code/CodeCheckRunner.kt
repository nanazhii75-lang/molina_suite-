package com.molina.suite.feature.code

import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ShellCommand
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import java.io.File

/** Hasil menjalankan pemeriksa: selesai (lolos atau gagal) atau tidak bisa dijalankan sama sekali. */
internal sealed interface CodeCheckRunResult {
    data class Finished(val outcome: CodeCheckOutcome) : CodeCheckRunResult
    data class Unavailable(val reason: String) : CodeCheckRunResult
}

/**
 * Menjalankan pemeriksa sebuah berkas di dalam Debian lewat kontrak [ShellCommandRunner] di
 * core-common, tanpa mengenal kelas engine terminal. Yang dijalankan hanya pemeriksa (lihat
 * [CodeCheckLanguage.checkScript]); program yang diperiksa tidak pernah dieksekusi.
 *
 * [filesDir] adalah folder files aplikasi; bash milik Molina Terminal ada di usr/bin di bawahnya.
 * Membatalkan coroutine hanya berhenti menunggu hasil; pemeriksa yang sudah berjalan berhenti
 * sendiri paling lama setelah batas waktu bahasanya.
 */
internal class CodeCheckRunner(
    private val filesDir: File,
    private val shell: ShellCommandRunner
) {

    suspend fun run(language: CodeCheckLanguage, file: File): CodeCheckRunResult {
        val script = language.checkScript(file.absolutePath)
            ?: return CodeCheckRunResult.Unavailable("Jalur berkas tidak valid: ${file.absolutePath}")
        val command = ShellCommand(
            executable = File(filesDir, BASH_RELATIVE_PATH).absolutePath,
            arguments = listOf("-c", ScriptRunLine.wrapForDebian(script)),
            label = "Cek ${file.name}"
        )
        return when (val result = shell.runInBackground(command)) {
            is ShellResult.Failed -> CodeCheckRunResult.Unavailable(result.reason)
            is ShellResult.Finished ->
                if (result.exitCode == 0 && result.stdout.contains(NO_DEBIAN_MARKER)) {
                    // Pembungkus Debian hanya mencetak pesan ini (kode keluar 0) bila proot-distro tidak ada.
                    CodeCheckRunResult.Unavailable("Debian belum terpasang. Pasang dulu lewat Setting, bagian Debian.")
                } else {
                    CodeCheckRunResult.Finished(CodeCheckParser.interpret(result.exitCode, result.stdout, result.stderr))
                }
        }
    }

    private companion object {
        const val BASH_RELATIVE_PATH = "usr/bin/bash"
        const val NO_DEBIAN_MARKER = "Debian belum terpasang"
    }
}
