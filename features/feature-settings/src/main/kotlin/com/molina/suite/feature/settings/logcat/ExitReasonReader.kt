package com.molina.suite.feature.settings.logcat

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Membaca sebab berakhirnya proses sebelumnya dari sistem. Hanya ada di Android 11 ke atas;
 * di versi lebih lama hasilnya null dan penilaian memakai catatan sendiri ([ProcessMarker]).
 */
internal object ExitReasonReader {

    /** Sebab berakhirnya proses [pid] milik aplikasi ini, atau null bila sistem tidak menyimpannya. */
    fun cause(context: Context, pid: Int): ExitCause? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            query(context, pid)
        } catch (e: RuntimeException) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun query(context: Context, pid: Int): ExitCause? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = manager.getHistoricalProcessExitReasons(context.packageName, pid, 1).firstOrNull() ?: return null
        val kind = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> ExitKind.JAVA_CRASH
            ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitKind.NATIVE_CRASH
            ApplicationExitInfo.REASON_ANR -> ExitKind.ANR
            ApplicationExitInfo.REASON_LOW_MEMORY -> ExitKind.LOW_MEMORY
            ApplicationExitInfo.REASON_SIGNALED -> ExitKind.SIGNALED
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> ExitKind.RESOURCE
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED -> ExitKind.NORMAL
            else -> ExitKind.OTHER
        }
        return ExitCause(kind, info.description)
    }
}
