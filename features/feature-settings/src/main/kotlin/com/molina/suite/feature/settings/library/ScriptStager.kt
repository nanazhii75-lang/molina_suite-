package com.molina.suite.feature.settings.library

import com.molina.suite.core.storage.MolinaStorage
import java.io.File
import java.io.IOException

/**
 * Menulis isi entri multi-baris ke berkas di folder bersama supaya terminal cukup
 * mengetik satu baris (`bash <berkas>`). Path hasil memakai /sdcard/..., yang sama
 * dari shell host maupun dari dalam Debian. Panggil dari Dispatchers.IO.
 */
internal object ScriptStager {

    const val SCRIPTS_DIR_NAME = "scripts"

    fun stage(entryId: String, script: String): LibraryResult<String> {
        val safeId = entryId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (safeId.isEmpty()) return LibraryResult.Failure("ID entri tidak valid")
        val dir = File(MolinaStorage.sharedRoot(), SCRIPTS_DIR_NAME)
        val target = File(dir, "$safeId.sh")
        val temp = File(dir, "$safeId.sh.tmp")
        return try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                return LibraryResult.Failure("Gagal membuat folder ${dir.path}; izin penyimpanan belum diberikan")
            }
            val text = script.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n') + "\n"
            temp.writeText(text, Charsets.UTF_8)
            if (temp.renameTo(target)) {
                LibraryResult.Ok("/sdcard/${MolinaStorage.SHARED_DIR_NAME}/$SCRIPTS_DIR_NAME/$safeId.sh")
            } else {
                temp.delete()
                LibraryResult.Failure("Gagal mengganti berkas ${target.path}")
            }
        } catch (e: IOException) {
            temp.delete()
            LibraryResult.Failure("Gagal menulis ${target.path}: ${e.message}")
        } catch (e: SecurityException) {
            temp.delete()
            LibraryResult.Failure("Izin menulis ${target.path} ditolak: ${e.message}")
        }
    }
}
