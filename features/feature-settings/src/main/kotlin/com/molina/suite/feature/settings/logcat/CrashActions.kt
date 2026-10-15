package com.molina.suite.feature.settings.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.molina.suite.feature.settings.R
import java.io.File
import java.io.IOException

/** Aksi pada teks laporan crash yang dipakai notifikasi dan layar laporan. */
internal object CrashActions {

    private const val SHARE_MAX_CHARS = 120_000

    fun copy(context: Context, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("molina", text))
        return true
    }

    /** Intent pemilih aplikasi untuk membagikan laporan sebagai teks; teks yang terlalu panjang dipotong. */
    fun shareIntent(context: Context, name: String, text: String): Intent {
        val body = if (text.length > SHARE_MAX_CHARS) {
            text.take(SHARE_MAX_CHARS) + "\n\n" + context.getString(R.string.crash_share_clipped)
        } else {
            text
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .putExtra(Intent.EXTRA_TEXT, body)
        return Intent.createChooser(send, context.getString(R.string.crash_share_title))
    }
}

/** Mencari berkas laporan yang boleh dibuka: hanya "crash-*.txt" langsung di folder crash aplikasi. */
internal object CrashFiles {

    /** Berkas pada [path] bila sah; bila tidak (atau [path] null), laporan terbaru; null bila tidak ada. */
    fun resolve(context: Context, path: String?): File? {
        val dir = CrashRecorder.crashDir(context.applicationContext)
        if (path != null) {
            val requested = File(path)
            if (isInside(dir, requested) && requested.isFile) return requested
        }
        return CrashLogStore.latest(dir)
    }

    private fun isInside(dir: File, file: File): Boolean = try {
        CrashReport.isCrashFile(file.name) && file.canonicalFile.parentFile == dir.canonicalFile
    } catch (e: IOException) {
        false
    }
}
