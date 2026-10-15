package com.molina.suite.feature.settings.logcat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.molina.suite.feature.settings.R
import java.io.File

/** Tombol Salin di notifikasi crash: menyalin seluruh laporan ke papan klip tanpa membuka layar apa pun. */
class CrashActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY) return
        val app = context.applicationContext
        val file = CrashFiles.resolve(app, intent.getStringExtra(EXTRA_FILE))
        val text = file?.let { CrashLogStore.read(it) }
        val message = when {
            text == null -> R.string.crash_missing
            CrashActions.copy(app, text) -> R.string.crash_copied
            else -> R.string.logcat_copy_failed
        }
        if (text != null) ProcessTracking.crashNotice(app).markSeen()
        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_COPY = "com.molina.suite.action.COPY_CRASH_REPORT"
        private const val EXTRA_FILE = "file"

        fun copyIntent(context: Context, report: File): Intent =
            Intent(context, CrashActionReceiver::class.java)
                .setAction(ACTION_COPY)
                .putExtra(EXTRA_FILE, report.absolutePath)
    }
}
