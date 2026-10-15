package com.molina.suite.feature.settings.logcat

import android.os.Environment
import com.molina.suite.core.storage.MolinaStorage
import java.io.File
import java.io.IOException

/**
 * Menyalin laporan crash ke folder bersama /sdcard/Molina/crash/ supaya tetap bisa dibaca dari
 * pengelola berkas atau Terminal walaupun aplikasi tidak bisa dibuka. Hanya berkas "crash-*.txt"
 * di folder itu yang dipangkas (paling banyak [CrashLogStore.KEEP] laporan terbaru).
 */
internal object CrashExporter {

    private const val DIR_NAME = "crash"

    sealed interface Result {
        data class Saved(val file: File) : Result
        data class Failed(val reason: String) : Result
    }

    fun exportToShared(source: File): Result {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            return Result.Failed("penyimpanan eksternal tidak terpasang ($state)")
        }
        val dir = File(MolinaStorage.sharedRoot(), DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            return Result.Failed("folder " + dir.absolutePath + " tidak bisa dibuat (izin penyimpanan?)")
        }
        return try {
            val target = File(dir, source.name)
            source.copyTo(target, overwrite = true)
            CrashLogStore.prune(dir, CrashLogStore.KEEP)
            Result.Saved(target)
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
