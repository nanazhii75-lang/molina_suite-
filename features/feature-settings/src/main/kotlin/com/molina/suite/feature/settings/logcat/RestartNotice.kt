package com.molina.suite.feature.settings.logcat

import android.content.Context
import java.util.Date

/**
 * Menyusun penanda restart saat aplikasi dimulai: menilai bagaimana proses sebelumnya berakhir
 * ([ProcessMarker]), menulis laporan sendiri bila proses itu mati tanpa catatan crash Java, lalu
 * mengembalikan baris penanda untuk daftar Logcat. Tidak pernah melempar.
 */
internal object RestartNotice {

    private const val TAIL_LINES = 400
    private const val DUMP_TIMEOUT_MS = 2500L

    /** Baris penanda untuk [stamp] ("MM-DD HH:MM:SS.mmm"), atau null bila tidak ada yang perlu ditandai. */
    fun detect(context: Context, stamp: String): LogcatEntry? {
        return try {
            val tracker = ProcessTracking.get(context)
            val previous = tracker.takePrevious() ?: return null
            val cause = ExitReasonReader.cause(context, previous.pid)
            val event = ProcessMarker.evaluate(previous, tracker.pid, cause) ?: return null
            if (event.needsReport) writeReport(context, event)
            ProcessMarker.toEntry(event, tracker.pid, stamp)
        } catch (e: Exception) {
            null
        }
    }

    /** Laporan untuk proses yang mati tanpa catatan crash; gagal menulis hanya berarti laporannya tidak ada. */
    private fun writeReport(context: Context, event: ProcessEvent) {
        try {
            val now = Date()
            val report = ProcessDeathReport.build(
                time = now,
                appVersion = DeviceInfo.appVersion(context),
                device = DeviceInfo.summary(),
                event = event,
                logLines = LogcatReader.dumpTail(TAIL_LINES, DUMP_TIMEOUT_MS)
            )
            CrashLogStore.write(CrashRecorder.crashDir(context.applicationContext), now, report)
                ?.let { CrashDelivery.deliver(context, it) }
        } catch (e: Exception) {
            // Penanda di daftar Logcat tetap dibuat.
        }
    }
}
