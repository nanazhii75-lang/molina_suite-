package com.molina.suite.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Pembungkus [ShellCommandRunner] yang mencatat setiap perintah sebagai satu aktivitas
 * [ActivityLog] (sumber Terminal), sehingga ikut terkumpul di layar Logcat bersama mpv, Terminal,
 * dan Code. Perilaku dan hasil perintah tidak diubah.
 *
 * Yang dicatat: mulai, hasil, lama berjalan, dan (bila kode keluar bukan nol) beberapa baris
 * terakhir keluaran. Isi argumen tidak pernah dicatat karena bisa memuat skrip panjang atau data
 * pribadi; pengenalnya adalah [ShellCommand.label].
 */
class LoggingShellCommandRunner(
    private val delegate: ShellCommandRunner,
    private val log: ActivityLog = Activities.log
) : ShellCommandRunner {

    override suspend fun runInBackground(command: ShellCommand): ShellResult {
        val activity = log.begin(ActivitySource.TERMINAL, ShellLogFormat.name(command))
        activity.note("mulai: ${command.executable.substringAfterLast('/')}")
        val result = try {
            delegate.runInBackground(command)
        } catch (e: CancellationException) {
            activity.end(false, "dibatalkan; proses yang sudah berjalan tidak dihentikan", ActivityPriority.WARN)
            throw e
        } catch (e: Exception) {
            activity.end(false, "pengecualian: ${e.message ?: e.javaClass.simpleName}")
            throw e
        }
        when (result) {
            is ShellResult.Failed -> activity.end(false, "gagal dijalankan: ${result.reason}")
            is ShellResult.Finished -> if (result.exitCode == 0) {
                activity.end(true)
            } else {
                ShellLogFormat.tail(result.stderr.ifBlank { result.stdout })
                    .forEach { activity.note(it, ActivityPriority.WARN) }
                activity.end(false, "kode keluar ${result.exitCode}", ActivityPriority.WARN)
            }
        }
        return result
    }
}
