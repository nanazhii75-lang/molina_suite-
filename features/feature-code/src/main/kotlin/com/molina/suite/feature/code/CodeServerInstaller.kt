package com.molina.suite.feature.code

import android.app.Application
import android.os.StatFs
import com.molina.suite.core.common.ShellCommand
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Memasang code-server ke dalam Debian lewat skrip yang dijalankan oleh engine
 * terminal. Berjalan di scope level aplikasi sehingga berpindah tab tidak
 * membatalkan pemasangan. Keberhasilan ditentukan dari berkas penanda versi di
 * rootfs, bukan hanya dari hasil perintah.
 */
internal class CodeServerInstaller(
    private val application: Application,
    private val runner: ShellCommandRunner,
    private val layout: CodeServerLayout
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installing = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<CodeInstallState>(detect())

    val state: StateFlow<CodeInstallState> = mutableState.asStateFlow()

    /** Membaca ulang keadaan dari disk. Tidak mengganggu pemasangan atau pesan gagal. */
    fun refresh() {
        if (installing.get()) return
        if (mutableState.value is CodeInstallState.Failed) return
        mutableState.value = detect()
    }

    /** Memulai pemasangan bila belum berjalan. */
    fun install() {
        if (!installing.compareAndSet(false, true)) return
        scope.launch {
            try {
                mutableState.value = runInstall()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = CodeInstallState.Failed(
                    "Pemasangan terhenti: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                installing.set(false)
            }
        }
    }

    private fun detect(): CodeInstallState = when {
        !layout.hasRootfs() -> CodeInstallState.RootfsMissing
        layout.isInstalled() -> CodeInstallState.Installed(CodeServerRelease.VERSION)
        else -> CodeInstallState.NotInstalled(CodeServerRelease.SIZE_BYTES)
    }

    private suspend fun runInstall(): CodeInstallState {
        if (!layout.hasRootfs()) return CodeInstallState.RootfsMissing
        if (layout.isInstalled()) return CodeInstallState.Installed(CodeServerRelease.VERSION)

        val available = StatFs(application.filesDir.path).availableBytes
        if (available < REQUIRED_FREE_BYTES) {
            return CodeInstallState.Failed(
                "Ruang penyimpanan kurang: tersedia ${available / MEGABYTE} MB, " +
                    "dibutuhkan sekitar ${REQUIRED_FREE_BYTES / MEGABYTE} MB"
            )
        }

        try {
            prepareScript()
        } catch (e: IOException) {
            return CodeInstallState.Failed("Gagal menyiapkan skrip pemasangan: ${e.message}")
        }

        mutableState.value = CodeInstallState.Installing(
            InstallPhase.PREPARE, 0L, CodeServerRelease.SIZE_BYTES
        )

        val command = ShellCommand(
            executable = layout.prootDistro.path,
            arguments = listOf(
                "login", "debian", "--", "/bin/bash", CodeServerLayout.GUEST_INSTALL_SCRIPT
            ),
            label = "Molina Code: pasang code-server"
        )
        val run = scope.async { runner.runInBackground(command) }

        var result: ShellResult? = null
        while (true) {
            val rawPhase = layout.readPhase()
            mutableState.value = CodeInstallState.Installing(
                phaseOf(rawPhase),
                layout.downloadFile.length(),
                CodeServerRelease.SIZE_BYTES
            )
            if (run.isCompleted) {
                result = run.await()
                break
            }
            if (rawPhase == CodeServerLayout.PHASE_DONE || rawPhase == CodeServerLayout.PHASE_FAILED) {
                // Skrip sudah selesai; beri waktu singkat agar hasil perintah tiba, lalu lanjut.
                result = withTimeoutOrNull(RESULT_GRACE_MS) { run.await() }
                break
            }
            delay(POLL_INTERVAL_MS)
        }
        run.cancel()

        return if (layout.isInstalled()) {
            CodeInstallState.Installed(CodeServerRelease.VERSION)
        } else {
            CodeInstallState.Failed(failureReason(result))
        }
    }

    private fun prepareScript() {
        if (!layout.molinaDir.isDirectory && !layout.molinaDir.mkdirs()) {
            throw IOException("Tidak bisa membuat ${layout.molinaDir}")
        }
        layout.statusFile.delete()
        layout.installLog.delete()

        val script = application.assets.open(INSTALL_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("@VERSION@", CodeServerRelease.VERSION)
            .replace("@URL@", CodeServerRelease.DOWNLOAD_URL)
            .replace("@SIZE@", CodeServerRelease.SIZE_BYTES.toString())
            .replace("@SHA256@", CodeServerRelease.SHA256)
        layout.installScript.writeText(script)
        layout.installScript.setExecutable(true, false)
    }

    private fun phaseOf(raw: String): InstallPhase = when (raw) {
        CodeServerLayout.PHASE_DOWNLOAD -> InstallPhase.DOWNLOAD
        CodeServerLayout.PHASE_VERIFY -> InstallPhase.VERIFY
        CodeServerLayout.PHASE_EXTRACT -> InstallPhase.EXTRACT
        CodeServerLayout.PHASE_FINISH, CodeServerLayout.PHASE_DONE -> InstallPhase.FINISH
        else -> InstallPhase.PREPARE
    }

    private fun failureReason(result: ShellResult?): String {
        val logTail = layout.readLogTail(MAX_LOG_LINES)
        if (logTail.isNotEmpty()) return logTail
        return when (result) {
            is ShellResult.Failed -> result.reason
            is ShellResult.Finished ->
                "Skrip pemasangan berakhir dengan kode ${result.exitCode}: ${result.stderr.trim()}"
            null -> "Pemasangan tidak melaporkan hasil"
        }
    }

    private companion object {
        const val INSTALL_ASSET = "molina-code/code-install.sh"
        const val REQUIRED_FREE_BYTES = 1_200_000_000L
        const val MEGABYTE = 1024L * 1024L
        const val POLL_INTERVAL_MS = 500L
        const val RESULT_GRACE_MS = 5_000L
        const val MAX_LOG_LINES = 6
    }
}
