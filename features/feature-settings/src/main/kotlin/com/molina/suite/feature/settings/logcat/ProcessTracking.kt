package com.molina.suite.feature.settings.logcat

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pelacak proses milik aplikasi: satu [ProcessTracker] per proses yang catatannya disimpan di
 * folder status privat, plus pemantau apakah aplikasi sedang di depan layar. Penangan crash dan
 * perekam log memakai pelacak yang sama lewat [get].
 */
internal object ProcessTracking {

    private const val STATE_DIR_NAME = "logcat-state"
    private const val PROCESS_FILE_NAME = "process.state"
    private const val SEEN_FILE_NAME = "crash-seen"

    @Volatile private var instance: ProcessTracker? = null
    private val installed = AtomicBoolean(false)

    /** Folder privat untuk catatan status (tidak ikut dicadangkan, tidak ikut dibersihkan tombol Bersihkan). */
    fun stateDir(context: Context): File = File(context.applicationContext.noBackupFilesDir, STATE_DIR_NAME)

    /** Pelacak proses ini; dibuat dan dimulai pada panggilan pertama sehingga catatan lama terbaca sebelum ditimpa. */
    fun get(context: Context): ProcessTracker {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val store = ProcessStateStore(File(stateDir(context), PROCESS_FILE_NAME))
            val tracker = ProcessTracker(store, android.os.Process.myPid())
            tracker.begin()
            instance = tracker
            return tracker
        }
    }

    /** Penanda laporan crash yang belum dibuka. */
    fun crashNotice(context: Context): CrashNotice {
        val app = context.applicationContext
        return CrashNotice(CrashRecorder.crashDir(app), File(stateDir(app), SEEN_FILE_NAME))
    }

    /** Mencatat bahwa proses ini sengaja ditutup (tombol Exit), supaya proses berikutnya tidak menilainya mati mendadak. */
    fun markCleanExit(context: Context) {
        get(context).markCleanExit()
    }

    /** Mulai melacak sejak aplikasi dibuka; aman dipanggil berulang. */
    fun install(application: Application) {
        val tracker = get(application)
        if (!installed.compareAndSet(false, true)) return
        crashNotice(application).initBaseline()
        application.registerActivityLifecycleCallbacks(ForegroundWatcher(tracker))
    }

    /**
     * Mencatat ke disk apakah aplikasi sedang tampil di depan layar, supaya proses berikutnya
     * bisa membedakan berhenti saat dipakai (tidak wajar) dari berhenti di latar belakang.
     * Penulisan dilakukan di thread sendiri agar thread utama tidak menunggu disk.
     */
    private class ForegroundWatcher(private val tracker: ProcessTracker) : Application.ActivityLifecycleCallbacks {

        private val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ProcessState").apply { isDaemon = true }
        }
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
            if (started == 1) post(true)
        }

        override fun onActivityStopped(activity: Activity) {
            if (started > 0) started--
            if (started == 0 && !activity.isChangingConfigurations) post(false)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        private fun post(foreground: Boolean) {
            try {
                executor.execute { tracker.setForeground(foreground) }
            } catch (e: RejectedExecutionException) {
                // Pelacak berhenti bersama proses; tidak ada yang perlu dilakukan.
            }
        }
    }
}
