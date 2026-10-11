package com.molina.suite.feature.settings.run

import java.io.File
import java.io.IOException

internal enum class TimeoutReason {
    /** Berkas status tidak pernah muncul: terminal tidak menjalankan skrip. */
    NOT_STARTED,

    /** Status "berjalan" tidak diperbarui terlalu lama: proses macet atau dihentikan paksa. */
    STALLED
}

/** Keadaan proses pada satu kali pembacaan berkas status. */
internal sealed interface RunSnapshot {
    data class Waiting(val elapsedMs: Long) : RunSnapshot
    data class Active(val status: RunStatus) : RunSnapshot
    data class Finished(val status: RunStatus) : RunSnapshot
    data class TimedOut(val reason: TimeoutReason, val last: RunStatus?) : RunSnapshot
}

/** Membaca berkas status dan menilai apakah proses menunggu, berjalan, selesai, atau macet. */
internal object RunWatcher {

    const val START_TIMEOUT_MS = 30_000L
    const val STALL_TIMEOUT_MS = 60_000L

    /** Pemasangan paket lewat apt tidak menulis status, jadi diberi batas yang jauh lebih longgar. */
    const val INSTALL_TIMEOUT_MS = 600_000L

    private const val INSTALL_STAGE = "install"

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun read(file: File, startedAt: Long, now: Long = System.currentTimeMillis()): RunSnapshot {
        val text = try {
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
        val status = text?.let { RunStatus.parse(it) }
        if (status == null) {
            val elapsed = now - startedAt
            return if (elapsed > START_TIMEOUT_MS) {
                RunSnapshot.TimedOut(TimeoutReason.NOT_STARTED, null)
            } else {
                RunSnapshot.Waiting(elapsed)
            }
        }
        return when (status.state) {
            RunState.DONE, RunState.FAILED -> RunSnapshot.Finished(status)
            RunState.RUNNING -> {
                val limit = if (status.stage == INSTALL_STAGE) INSTALL_TIMEOUT_MS else STALL_TIMEOUT_MS
                if (now - file.lastModified() > limit) {
                    RunSnapshot.TimedOut(TimeoutReason.STALLED, status)
                } else {
                    RunSnapshot.Active(status)
                }
            }
        }
    }
}
