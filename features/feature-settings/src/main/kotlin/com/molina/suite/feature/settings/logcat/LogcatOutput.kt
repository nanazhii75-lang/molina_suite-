package com.molina.suite.feature.settings.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keluaran layar Logcat: menyimpan ke /sdcard/Molina/Logs, menyalin baris terakhir, dan menampilkan
 * crash terakhir. Layar hanya memanggil metode di sini dan menampilkan pesan yang dikembalikan.
 */
internal class LogcatOutput(private val appContext: Context) {

    /** Menyimpan [entries] ke berkas; mengembalikan pesan hasil untuk pengguna. */
    suspend fun save(entries: List<LogcatEntry>): String = withContext(Dispatchers.IO) {
        when (val ensured = MolinaStorage.ensureDirectories()) {
            is MolinaStorage.EnsureResult.Failed ->
                appContext.getString(R.string.logcat_save_failed, ensured.reason)
            is MolinaStorage.EnsureResult.Ready ->
                when (val result = LogcatExporter.save(entries, MolinaStorage.logsDir())) {
                    is LogcatExporter.Result.Saved -> appContext.getString(
                        R.string.logcat_saved,
                        result.lines,
                        MolinaStorage.SHARED_DIR_NAME + "/" + MolinaStorage.LOGS_DIR_NAME + "/" + result.file.name
                    )
                    is LogcatExporter.Result.Failed ->
                        appContext.getString(R.string.logcat_save_failed, result.reason)
                }
        }
    }

    /** Menyalin [RECENT_LINES] baris terakhir dari [entries]; mengembalikan pesan hasil. */
    fun copyRecent(entries: List<LogcatEntry>): String {
        if (entries.isEmpty()) return appContext.getString(R.string.logcat_nothing)
        val copied = minOf(entries.size, RECENT_LINES)
        return if (copy(LogcatExporter.tail(entries, RECENT_LINES))) {
            appContext.getString(R.string.logcat_recent_copied, copied)
        } else {
            appContext.getString(R.string.logcat_copy_failed)
        }
    }

    /** Menampilkan laporan crash terakhir di dialog; [host] harus konteks Activity. */
    fun showLastCrash(host: Context) {
        val file = CrashLogStore.latest(CrashRecorder.crashDir(appContext))
        val text = file?.let { CrashLogStore.read(it) }
        if (file == null || text == null) {
            Toast.makeText(appContext, R.string.logcat_no_crash, Toast.LENGTH_LONG).show()
            return
        }
        val padding = (DIALOG_PADDING_DP * host.resources.displayMetrics.density).toInt()
        val body = TextView(host).apply {
            this.text = text
            typeface = Typeface.MONOSPACE
            textSize = DIALOG_TEXT_SP
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, padding)
        }
        MaterialAlertDialogBuilder(host)
            .setTitle(host.getString(R.string.logcat_crash_title, file.name))
            .setView(ScrollView(host).apply { addView(body) })
            .setPositiveButton(R.string.logcat_crash_copy) { _, _ ->
                val message = if (copy(text)) R.string.logcat_crash_copied else R.string.logcat_copy_failed
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.logcat_close, null)
            .show()
    }

    private fun copy(text: String): Boolean {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("molina", text))
        return true
    }

    private companion object {
        const val RECENT_LINES = 200
        const val DIALOG_PADDING_DP = 16
        const val DIALOG_TEXT_SP = 11f
    }
}
