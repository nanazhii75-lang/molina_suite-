package com.molina.suite.feature.code

import android.app.Application
import android.os.SystemClock
import android.system.OsConstants
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.ShellCommand
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Menjalankan dan menghentikan server code-server di dalam Debian. Server
 * dijalankan lewat [ShellCommandRunner] sebagai proses latar belakang, dipantau
 * lewat /healthz dan pid, lalu dihentikan langsung lewat sinyal ke pohon
 * prosesnya. Semua perubahan keadaan diserialkan oleh satu mutex.
 */
internal class CodeServerController(
    private val application: Application,
    private val runner: ShellCommandRunner,
    private val layout: CodeServerLayout,
    private val keepAlive: BackgroundKeepAlive
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val probe = CodeServerHealthProbe()
    private val inspector = CodeProcessInspector()
    private val credentials = CodeServerCredentials(layout.passwordFile)
    private val mutableState = MutableStateFlow<CodeServerState>(CodeServerState.Stopped)
    private var monitor: Job? = null

    val state: StateFlow<CodeServerState> = mutableState.asStateFlow()

    init {
        adoptRunningServer()
    }

    /** Menjalankan server bila belum berjalan. */
    fun start() {
        scope.launch {
            lock.withLock {
                val current = mutableState.value
                if (current == CodeServerState.Starting ||
                    current == CodeServerState.Running ||
                    current == CodeServerState.Stopping
                ) {
                    return@withLock
                }
                launchLocked()
            }
        }
    }

    /** Menghentikan server; juga membersihkan keadaan gagal. */
    fun stop() {
        scope.launch {
            lock.withLock {
                val current = mutableState.value
                if (current == CodeServerState.Stopped || current == CodeServerState.Stopping) {
                    return@withLock
                }
                monitor?.cancel()
                monitor = null
                mutableState.value = CodeServerState.Stopping
                if (current == CodeServerState.Starting) awaitPidFile()
                terminateServer()
                layout.pidFile.delete()
                keepAlive.release()
                mutableState.value = CodeServerState.Stopped
            }
        }
    }

    /** Mengadopsi server yang masih hidup dari sesi aplikasi sebelumnya. */
    private fun adoptRunningServer() {
        scope.launch {
            lock.withLock {
                if (mutableState.value != CodeServerState.Stopped) return@withLock
                val pid = layout.readServerPid() ?: return@withLock
                if (inspector.isAlive(pid) && probe.isHealthy()) {
                    adoptLocked()
                } else {
                    layout.pidFile.delete()
                }
            }
        }
    }

    private fun adoptLocked() {
        keepAlive.acquire()
        mutableState.value = CodeServerState.Running
        monitor = scope.launch { superviseRunning(null) }
    }

    private suspend fun launchLocked() {
        if (!layout.hasRootfs() || !layout.isInstalled()) {
            mutableState.value = CodeServerState.Failed("code-server belum terpasang")
            return
        }
        if (probe.isHealthy()) {
            val pid = layout.readServerPid()
            if (pid != null && inspector.isAlive(pid)) {
                adoptLocked()
            } else {
                mutableState.value = CodeServerState.Failed(
                    "Port ${CodeServerEndpoint.PORT} sudah dipakai proses lain. " +
                        "Tutup aplikasi yang memakainya lalu coba lagi."
                )
            }
            return
        }
        try {
            prepareFiles()
        } catch (e: IOException) {
            mutableState.value = CodeServerState.Failed("Gagal menyiapkan skrip start: ${e.message}")
            return
        }
        mutableState.value = CodeServerState.Starting
        keepAlive.acquire()
        val run = scope.async { runner.runInBackground(startCommand()) }
        monitor = scope.launch { superviseStartup(run) }
    }

    private fun startCommand(): ShellCommand = ShellCommand(
        executable = layout.prootDistro.path,
        arguments = listOf("login", "debian", "--", "/bin/bash", CodeServerLayout.GUEST_RUN_SCRIPT),
        label = "Molina Code: code-server"
    )

    private fun prepareFiles() {
        if (!layout.molinaDir.isDirectory && !layout.molinaDir.mkdirs()) {
            throw IOException("Tidak bisa membuat ${layout.molinaDir}")
        }
        layout.pidFile.delete()
        credentials.ensure()
        val script = application.assets.open(RUN_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("@VERSION@", CodeServerRelease.VERSION)
            .replace("@BIND@", CodeServerEndpoint.BIND_ADDRESS)
            .replace("@APP_NAME@", APP_NAME)
            .replace("@WORKSPACE@", WORKSPACE_PATH)
        layout.runScript.writeText(script)
        layout.runScript.setExecutable(true, false)
    }

    private suspend fun superviseStartup(run: Deferred<ShellResult>) {
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            if (probe.isHealthy()) break
            if (run.isCompleted) {
                failStartup(startupFailureReason(run.await(), false))
                return
            }
            if (SystemClock.elapsedRealtime() - startedAt > START_TIMEOUT_MS) {
                failStartup(startupFailureReason(null, true))
                return
            }
            delay(POLL_INTERVAL_MS)
        }
        val promoted = lock.withLock {
            if (mutableState.value == CodeServerState.Starting) {
                mutableState.value = CodeServerState.Running
                true
            } else {
                false
            }
        }
        if (promoted) superviseRunning(run)
    }

    private suspend fun superviseRunning(run: Deferred<ShellResult>?) {
        while (true) {
            delay(MONITOR_INTERVAL_MS)
            val pid = layout.readServerPid()
            val alive = pid != null && inspector.isAlive(pid)
            if (!alive || run?.isCompleted == true) break
        }
        lock.withLock {
            if (mutableState.value != CodeServerState.Running) return
            terminateServer()
            layout.pidFile.delete()
            keepAlive.release()
            val tail = layout.readServerLogTail(LOG_LINES)
            mutableState.value = CodeServerState.Failed(
                if (tail.isEmpty()) "Proses code-server berhenti tanpa pesan" else tail
            )
        }
    }

    private suspend fun failStartup(reason: String) {
        lock.withLock {
            if (mutableState.value != CodeServerState.Starting) return
            terminateServer()
            layout.pidFile.delete()
            keepAlive.release()
            mutableState.value = CodeServerState.Failed(reason)
        }
    }

    private fun startupFailureReason(result: ShellResult?, timedOut: Boolean): String {
        val tail = layout.readServerLogTail(LOG_LINES)
        val base = when {
            timedOut -> "code-server tidak menjawab dalam ${START_TIMEOUT_MS / 1000} detik"
            result is ShellResult.Failed -> result.reason
            result is ShellResult.Finished -> "code-server berhenti dengan kode ${result.exitCode}"
            else -> "code-server berhenti sebelum siap"
        }
        return if (tail.isEmpty()) base else "$base\n$tail"
    }

    /** Saat dihentikan ketika masih memulai, tunggu skrip menulis pid agar prosesnya bisa dimatikan. */
    private suspend fun awaitPidFile() {
        val startedAt = SystemClock.elapsedRealtime()
        while (layout.readServerPid() == null &&
            SystemClock.elapsedRealtime() - startedAt < PID_WAIT_MS
        ) {
            delay(POLL_INTERVAL_MS / 2)
        }
    }

    /** SIGTERM ke server, lalu SIGKILL ke sisa pohon prosesnya bila belum berhenti. */
    private suspend fun terminateServer() {
        val pid = layout.readServerPid() ?: return
        if (!inspector.isAlive(pid)) return
        val tree = inspector.descendants(pid)
        inspector.signal(pid, OsConstants.SIGTERM)
        val startedAt = SystemClock.elapsedRealtime()
        while (inspector.isAlive(pid) && SystemClock.elapsedRealtime() - startedAt < STOP_GRACE_MS) {
            delay(STOP_POLL_MS)
        }
        val remaining = (tree + inspector.descendants(pid) + pid).distinct().filter { inspector.isAlive(it) }
        remaining.forEach { inspector.signal(it, OsConstants.SIGKILL) }
    }

    private companion object {
        const val RUN_ASSET = "molina-code/code-run.sh"
        const val APP_NAME = "Molina Code"
        const val WORKSPACE_PATH = "/sdcard/Molina"
        const val START_TIMEOUT_MS = 120_000L
        const val POLL_INTERVAL_MS = 500L
        const val MONITOR_INTERVAL_MS = 2_000L
        const val PID_WAIT_MS = 20_000L
        const val STOP_GRACE_MS = 8_000L
        const val STOP_POLL_MS = 200L
        const val LOG_LINES = 6
    }
}
