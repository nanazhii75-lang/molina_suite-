package com.molina.suite.feature.code

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.IOException

/**
 * Menyiapkan Tinjauan: isi editor disalin ke cache aplikasi (bukan lewat Intent, supaya aman untuk
 * berkas besar), lalu Intent untuk [CodePreviewActivity] disusun. Panggil dari Dispatchers.IO.
 */
internal object CodePreviewLauncher {

    const val EXTRA_SOURCE = "com.molina.suite.feature.code.preview.SOURCE"
    const val EXTRA_KIND = "com.molina.suite.feature.code.preview.KIND"
    const val EXTRA_DISPLAY_PATH = "com.molina.suite.feature.code.preview.DISPLAY_PATH"
    const val EXTRA_BASE_DIR = "com.molina.suite.feature.code.preview.BASE_DIR"

    private const val DIRECTORY_NAME = "code-preview"

    /** Folder cache Tinjauan; hanya berkas di dalamnya yang boleh dimuat oleh Activity. */
    fun directory(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)

    /** Intent Tinjauan untuk [file] dengan isi [text]; null bila cache tidak bisa ditulis. */
    fun prepare(context: Context, kind: CodePreviewKind, file: File, text: String): Intent? {
        val dir = directory(context)
        return try {
            clear(dir)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val source = File(dir, "source." + kind.sourceExtension)
            source.writeText(text, Charsets.UTF_8)
            Intent(context, CodePreviewActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.absolutePath)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_DISPLAY_PATH, file.absolutePath)
                .putExtra(EXTRA_BASE_DIR, file.parentFile?.absolutePath)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** Menghapus semua berkas sementara Tinjauan. */
    fun clear(dir: File) {
        dir.listFiles()?.forEach { it.delete() }
    }
}
