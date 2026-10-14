package com.molina.suite.core.common

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pembungkus [ShellCommandRunner] yang mencatat setiap perintah ke logcat dengan tag
 * [LOG_TAG], sehingga ikut terkumpul di layar Logcat bersama log mpv, Terminal, dan Code.
 * Perilaku dan hasil perintah tidak diubah.
 *
 * Yang dicatat: mulai, kode keluar, lama berjalan, dan (bila kode keluar bukan nol) beberapa baris
 * terakhir keluaran. Isi argumen tidak pernah dicatat karena bisa memuat skrip panjang atau data
 * pribadi; pengenalnya adalah [ShellCommand.label].
 *
 * [write] menerima prioritas [Log] dan satu baris pesan; diganti hanya saat diuji.
 */
class LoggingShellCommandRunner(
    private val delegate: ShellCommandRunner,
    private val write: (priority: Int, message: String) -> Unit = { priority, message ->
        Log.println(priority, LOG_TAG, message)
    }
) : ShellCommandRunner {

    override suspend fun runInBackground(command: ShellCommand): ShellResult {
        val name = ShellLogFormat.name(command)
        write(Log.INFO, "[$name] mulai: ${command.executable.substringAfterLast('/')}")
        val startedAt = System.nanoTime()
        val result = try {
            delegate.runInBackground(command)
        } catch (e: CancellationException) {
            write(Log.WARN, "[$name] dibatalkan; proses yang sudah berjalan tidak dihentikan")
            throw e
        }
        val seconds = ShellLogFormat.seconds(System.nanoTime() - startedAt)
        when (result) {
            is ShellResult.Failed ->
                write(Log.ERROR, "[$name] gagal dijalankan setelah $seconds: ${result.reason}")
            is ShellResult.Finished -> if (result.exitCode == 0) {
                write(Log.INFO, "[$name] selesai kode 0 ($seconds)")
            } else {
                write(Log.WARN, "[$name] selesai kode ${result.exitCode} ($seconds)")
                val output = ShellLogFormat.tail(result.stderr.ifBlank { result.stdout })
                output.forEach { write(Log.WARN, "[$name] | $it") }
            }
        }
        return result
    }

    companion object {
        /** Tag logcat semua perintah shell yang dijalankan aplikasi sendiri. */
        const val LOG_TAG = "MolinaShell"
    }
}
