package com.molina.suite.feature.x11

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineLauncher
import com.molina.suite.core.common.EngineStatus
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Menjalankan X server lorie lewat shell Molina Terminal lalu membuka jendela X11.
 * Status mengikuti keberadaan proses server, bukan hasil perintah shell, karena
 * perintah itu baru selesai saat server berhenti.
 */
class X11Launcher(
    private val application: Application,
    private val shell: ShellCommandRunner,
    private val keepAlive: BackgroundKeepAlive
) : EngineLauncher {

    override val id: EngineId = EngineId.X11

    private val mutableStatus = MutableStateFlow(EngineStatus.STOPPED)
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = mutableError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private var serverJob: Job? = null
    private var watchJob: Job? = null

    @Volatile
    private var stopRequested = false

    override fun launch(host: Activity) {
        mutableError.value = null
        stopRequested = false
        try {
            host.startActivity(Intent().setClassName(host.packageName, X11ServerCommand.WINDOW_ACTIVITY))
        } catch (e: RuntimeException) {
            fail("Jendela X11 tidak bisa dibuka: ${e.message}")
            return
        }
        scope.launch { ensureServer() }
    }

    override fun stop() {
        stopRequested = true
        scope.launch {
            application.sendBroadcast(
                Intent(X11ServerCommand.ACTION_STOP).setPackage(application.packageName)
            )
            withContext(Dispatchers.IO) {
                X11ServerProbe.findPids(Process.myPid()).forEach { Process.killProcess(it) }
            }
        }
    }

    private suspend fun ensureServer() = lock.withLock {
        if (serverJob?.isActive == true) return@withLock
        val alive = withContext(Dispatchers.IO) { X11ServerProbe.findPids(Process.myPid()) }
        if (alive.isEmpty()) {
            mutableStatus.value = EngineStatus.STARTING
            serverJob = scope.launch { runServer() }
        }
        watchJob?.cancel()
        watchJob = scope.launch { watchServer() }
    }

    private suspend fun runServer() {
        val info = application.applicationInfo
        val command = X11ServerCommand.start(info.sourceDir, info.nativeLibraryDir)
        keepAlive.acquire()
        try {
            when (val result = shell.runInBackground(command)) {
                is ShellResult.Failed -> fail(result.reason)
                is ShellResult.Finished -> {
                    if (result.exitCode != 0 && !stopRequested) {
                        fail(describeExit(result))
                    }
                }
            }
        } finally {
            keepAlive.release()
        }
    }

    private suspend fun watchServer() {
        val begin = SystemClock.elapsedRealtime()
        var seen = false
        while (true) {
            val running = withContext(Dispatchers.IO) {
                X11ServerProbe.findPids(Process.myPid()).isNotEmpty()
            }
            if (running) {
                seen = true
                mutableStatus.value = EngineStatus.RUNNING
            } else if (seen) {
                mutableStatus.value = EngineStatus.STOPPED
                return
            } else if (SystemClock.elapsedRealtime() - begin > START_TIMEOUT_MS) {
                fail("X server tidak muncul. Pastikan Molina Terminal sudah terpasang (buka tab Terminal sekali).")
                return
            }
            delay(POLL_MS)
        }
    }

    private fun describeExit(result: ShellResult.Finished): String {
        val detail = result.stderr.trim().lineSequence().lastOrNull { it.isNotBlank() }
        return if (detail != null) {
            "X server berhenti (kode ${result.exitCode}): $detail"
        } else {
            "X server berhenti (kode ${result.exitCode})"
        }
    }

    private fun fail(message: String) {
        watchJob?.cancel()
        mutableError.value = message
        mutableStatus.value = EngineStatus.ERROR
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val START_TIMEOUT_MS = 30_000L
    }
}
