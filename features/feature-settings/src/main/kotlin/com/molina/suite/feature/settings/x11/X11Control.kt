package com.molina.suite.feature.settings.x11

import android.content.Context
import android.os.Process
import com.molina.suite.feature.settings.debian.DebianStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Titik masuk Setting untuk memantau dan menghentikan proses X11. Desktop dikenali dari berkas
 * pid yang ditulis molina-x11 di dalam Debian (HOME root), server dari argumen app_process.
 */
internal object X11Control {

    private const val PID_FILE = "root/.molina/x11-desktop.pid"

    private fun stopper() = X11Stopper(
        snapshot = { ProcTable.scan() },
        signal = { pid, signal -> Process.sendSignal(pid, signal) },
        pause = { delay(it) }
    )

    private fun pidFile(context: Context): File = File(DebianStatus.rootfsDir(context), PID_FILE)

    private fun readRoot(file: File): Int? = try {
        file.readText().trim().toIntOrNull()
    } catch (e: IOException) {
        null
    }

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun usage(context: Context): X11Usage {
        val table = ProcTable.scan()
        return X11Usage.read(table, readRoot(pidFile(context)), Process.myPid()) { ProcTable.rssKb(it) }
    }

    /** Menghentikan desktop sampai tuntas; tetap selesai walau layar ditinggalkan. */
    suspend fun stopDesktop(context: Context): X11Stopper.Outcome =
        withContext(NonCancellable + Dispatchers.IO) {
            val file = pidFile(context)
            val self = Process.myPid()
            val outcome = stopper().stop { table -> X11Selection.desktop(table, readRoot(file), self) }
            if (outcome.remaining.isEmpty()) file.delete()
            outcome
        }

    /** Memastikan tidak ada proses X server tersisa (setelah peluncur diminta berhenti). */
    suspend fun stopServer(): X11Stopper.Outcome =
        withContext(NonCancellable + Dispatchers.IO) {
            val self = Process.myPid()
            stopper().stop { table -> X11Selection.server(table, self) }
        }
}
