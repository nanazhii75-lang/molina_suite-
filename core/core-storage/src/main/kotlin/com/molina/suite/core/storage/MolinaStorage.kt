package com.molina.suite.core.storage

import android.os.Environment
import java.io.File

/**
 * Satu-satunya sumber lokasi folder bersama antar-modul molina-suite.
 * Modul lain tidak boleh menulis literal "/sdcard/Molina" sendiri.
 *
 * Membuat folder di penyimpanan eksternal pada API 26 membutuhkan izin
 * WRITE_EXTERNAL_STORAGE yang sudah diberikan; bila belum, [ensureDirectories]
 * mengembalikan [EnsureResult.Failed] dengan alasan yang jelas.
 */
object MolinaStorage {

    /** Nama folder bersama di penyimpanan eksternal: /sdcard/Molina/ (M kapital). */
    const val SHARED_DIR_NAME = "Molina"

    /** Sub-folder unduhan: /sdcard/Molina/Downloads/. */
    const val DOWNLOADS_DIR_NAME = "Downloads"

    sealed interface EnsureResult {
        data class Ready(val root: File) : EnsureResult
        data class Failed(val dir: File, val reason: String) : EnsureResult
    }

    @Suppress("DEPRECATION")
    fun sharedRoot(): File = File(Environment.getExternalStorageDirectory(), SHARED_DIR_NAME)

    fun downloadsDir(): File = File(sharedRoot(), DOWNLOADS_DIR_NAME)

    /** Membuat folder bersama beserta sub-foldernya bila belum ada. */
    fun ensureDirectories(): EnsureResult {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            return EnsureResult.Failed(
                sharedRoot(),
                "Penyimpanan eksternal tidak terpasang (status: $state)"
            )
        }
        for (dir in listOf(sharedRoot(), downloadsDir())) {
            if (dir.isDirectory) continue
            val created = try {
                dir.mkdirs()
            } catch (e: SecurityException) {
                return EnsureResult.Failed(dir, "Izin ditolak: ${e.message}")
            }
            if (!created && !dir.isDirectory) {
                return EnsureResult.Failed(
                    dir,
                    "Gagal membuat folder; izin penyimpanan belum diberikan atau penyimpanan tidak bisa ditulis"
                )
            }
        }
        return EnsureResult.Ready(sharedRoot())
    }
}
