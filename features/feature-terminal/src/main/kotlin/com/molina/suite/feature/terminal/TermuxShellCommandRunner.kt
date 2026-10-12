package com.molina.suite.feature.terminal

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.molina.suite.core.common.ShellCommand
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import com.molina.suite.terminal.app.TermuxService
import com.molina.suite.terminal.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Implementasi [ShellCommandRunner] lewat jalur internal engine terminal:
 * intent ACTION_SERVICE_EXECUTE ke TermuxService (tidak diekspor), sehingga
 * tidak melewati RunCommandService dan kebijakan allow-external-apps.
 */
class TermuxShellCommandRunner(private val application: Application) : ShellCommandRunner {

    private val requestIds = AtomicInteger(0)

    override suspend fun runInBackground(command: ShellCommand): ShellResult {
        validate(command)?.let { return ShellResult.Failed(it) }

        val requestId = requestIds.incrementAndGet()
        val resultAction = "${application.packageName}.shell_result.$requestId"

        return suspendCancellableCoroutine { continuation ->
            val cleanedUp = AtomicBoolean(false)
            var pendingIntent: PendingIntent? = null

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val result = parseResult(intent)
                    cleanUp(this, cleanedUp, pendingIntent)
                    if (continuation.isActive) continuation.resume(result)
                }
            }

            continuation.invokeOnCancellation { cleanUp(receiver, cleanedUp, pendingIntent) }

            try {
                ContextCompat.registerReceiver(
                    application,
                    receiver,
                    IntentFilter(resultAction),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                val resultIntent = Intent(resultAction).setPackage(application.packageName)
                pendingIntent = PendingIntent.getBroadcast(
                    application,
                    requestId,
                    resultIntent,
                    pendingIntentFlags()
                )

                val executableUri = Uri.Builder()
                    .scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
                    .path(command.executable)
                    .build()

                val executeIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri)
                executeIntent.setClass(application, TermuxService::class.java)
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, command.arguments.toTypedArray())
                command.workingDirectory?.let { executeIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, it) }
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true)
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, command.label)
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, pendingIntent)

                ContextCompat.startForegroundService(application, executeIntent)
            } catch (e: RuntimeException) {
                cleanUp(receiver, cleanedUp, pendingIntent)
                if (continuation.isActive) {
                    continuation.resume(
                        ShellResult.Failed("Gagal menjalankan perintah lewat engine terminal: ${e.message}")
                    )
                }
            }
        }
    }

    private fun validate(command: ShellCommand): String? {
        val executable = File(command.executable)
        if (!executable.isAbsolute) {
            return "Path executable harus absolut: ${command.executable}"
        }
        if (!executable.isFile) {
            return "Executable tidak ditemukan: ${command.executable}"
        }
        if (!executable.canExecute()) {
            return "Executable tidak bisa dijalankan: ${command.executable}"
        }
        val workingDirectory = command.workingDirectory
        if (workingDirectory != null && !File(workingDirectory).isDirectory) {
            return "Direktori kerja tidak ada: $workingDirectory"
        }
        return null
    }

    private fun cleanUp(receiver: BroadcastReceiver, cleanedUp: AtomicBoolean, pendingIntent: PendingIntent?) {
        if (!cleanedUp.compareAndSet(false, true)) return
        try {
            application.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver belum sempat terdaftar; tidak ada yang perlu dilepas.
        }
        pendingIntent?.cancel()
    }

    private fun pendingIntentFlags(): Int {
        // Engine mengisi hasil ke intent lewat PendingIntent.send, jadi harus mutable di API 31+.
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return flags
    }

    private fun parseResult(intent: Intent): ShellResult {
        val bundle = intent.getBundleExtra(RESULT_BUNDLE)
            ?: return ShellResult.Failed("Engine terminal tidak mengirim hasil perintah")
        val errorMessage = bundle.getString(RESULT_ERRMSG)?.trim().orEmpty()
        if (!bundle.containsKey(RESULT_EXIT_CODE)) {
            return ShellResult.Failed(errorMessage.ifEmpty { "Perintah berhenti tanpa kode keluar" })
        }
        return ShellResult.Finished(
            exitCode = bundle.getInt(RESULT_EXIT_CODE),
            stdout = bundle.getString(RESULT_STDOUT).orEmpty(),
            stderr = bundle.getString(RESULT_STDERR).orEmpty()
        )
    }

    private companion object {
        // Kunci bundel hasil engine (TermuxConstants, tanpa awalan nama paket).
        const val RESULT_BUNDLE = "result"
        const val RESULT_STDOUT = "stdout"
        const val RESULT_STDERR = "stderr"
        const val RESULT_EXIT_CODE = "exitCode"
        const val RESULT_ERRMSG = "errmsg"
    }
}
