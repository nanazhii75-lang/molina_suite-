package com.molina.suite.feature.settings.logcat

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Mencatat crash Java/Kotlin yang tidak tertangani: stack trace plus ekor logcat aplikasi ditulis
 * ke folder privat aplikasi yang hanya bisa dibaca molina-suite. Setelah itu crash diteruskan
 * ke penangan sebelumnya, jadi perilaku aplikasi tidak berubah. Crash native (mis. di libmpv)
 * tidak lewat sini.
 */
internal class CrashRecorder private constructor(
    private val context: Context,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            ProcessTracking.get(context).markJavaCrash()
        } catch (ignored: Throwable) {
            // Penanda untuk proses berikutnya; kegagalannya tidak boleh menutupi crash aslinya.
        }
        try {
            val report = record(thread, throwable)
            if (report != null) CrashDelivery.deliver(context, report)
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

    private fun record(thread: Thread, throwable: Throwable): File? {
        val now = Date()
        val report = CrashReport.build(
            time = now,
            threadName = thread.name,
            appVersion = DeviceInfo.appVersion(context),
            device = DeviceInfo.summary(),
            stackTrace = Log.getStackTraceString(throwable),
            logLines = LogcatReader.dumpTail(TAIL_LINES, DUMP_TIMEOUT_MS)
        )
        return CrashLogStore.write(crashDir(context), now, report)
    }

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
