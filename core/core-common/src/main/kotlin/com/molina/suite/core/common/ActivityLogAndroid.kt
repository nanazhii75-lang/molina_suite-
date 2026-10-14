package com.molina.suite.core.common

import android.util.Log

/** Menulis baris aktivitas ke logcat dengan tag [ActivityLog.LOG_TAG]; dibaca layar Logcat di Setting. */
object LogcatActivitySink : ActivitySink {
    override fun write(priority: ActivityPriority, message: String) {
        val level = when (priority) {
            ActivityPriority.INFO -> Log.INFO
            ActivityPriority.WARN -> Log.WARN
            ActivityPriority.ERROR -> Log.ERROR
        }
        Log.println(level, ActivityLog.LOG_TAG, message)
    }
}

/** Pencatat aktivitas milik aplikasi; semua modul memakai satu instans ini. */
object Activities {
    val log: ActivityLog by lazy(LazyThreadSafetyMode.PUBLICATION) { ActivityLog(LogcatActivitySink) }

    fun begin(source: ActivitySource, title: String): ActivityLog.Activity = log.begin(source, title)
}
