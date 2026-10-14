package com.molina.suite.feature.settings.logcat

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.molina.suite.core.storage.MolinaStorage
import java.io.File
import java.util.Date

/**
 * Mencatat crash Java/Kotlin yang tidak tertangani: stack trace plus ekor logcat aplikasi ditulis
 * ke folder privat aplikasi (selalu bisa ditulis) dan disalin ke /sdcard/Molina/Logs bila
 * penyimpanan bersama siap. Setelah itu crash diteruskan ke penangan sebelumnya, jadi perilaku
 * aplikasi tidak berubah. Crash native (mis. di libmpv) tidak lewat sini.
 */
internal class CrashRecorder private constructor(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            record(thread, throwable)
        } catch (ignored: Throwable) {
            // Pencatat tidak boleh menutupi atau mengubah crash aslinya.
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            Process.killProcess(Process.myPid())
            System.exit(EXIT_CODE)
        }
    }

    private fun record(thread: Thread, throwable: Throwable) {
        val now = Date()
        val report = CrashReport.build(
            time = now,
            threadName = thread.name,
            appVersion = appVersion(),
            device = deviceSummary(),
            stackTrace = Log.getStackTraceString(throwable),
            logLines = LogcatReader.dumpTail(TAIL_LINES, DUMP_TIMEOUT_MS)
        )
        val saved = CrashLogStore.write(crashDir(context), now, report)
        copyToShared(saved?.name ?: CrashReport.fileName(now), report)
    }

    /** Salinan best-effort ke folder bersama supaya mudah diambil; kegagalannya tidak dilaporkan. */
    private fun copyToShared(name: String, report: String) {
        try {
            if (MolinaStorage.ensureDirectories() is MolinaStorage.EnsureResult.Ready) {
                File(MolinaStorage.logsDir(), name).writeText(report, Charsets.UTF_8)
            }
        } catch (ignored: Exception) {
            // Berkas di folder privat sudah cukup.
        }
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "tidak diketahui"
    } catch (e: Exception) {
        "tidak diketahui"
    }

    private fun deviceSummary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            (Build.SUPPORTED_ABIS.firstOrNull() ?: "abi tidak diketahui")

    companion object {
        private const val TAIL_LINES = 400
        private const val DUMP_TIMEOUT_MS = 2500L
        private const val EXIT_CODE = 10
        private const val CRASH_DIR_NAME = "crash"

        /** Folder privat tempat laporan crash disimpan. */
        fun crashDir(context: Context): File = File(context.filesDir, CRASH_DIR_NAME)

        /** Memasang pencatat di atas penangan crash bawaan; aman dipanggil berulang. */
        fun install(application: Application) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashRecorder) return
            Thread.setDefaultUncaughtExceptionHandler(CrashRecorder(application.applicationContext, current))
        }
    }
}
