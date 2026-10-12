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

    const val EDIT_DIR_NAME = "edit"

    private fun safeId(entryId: String): String =
        entryId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    /**
     * Berkas salinan suntingan sebuah entri: <folder bersama>/scripts/edit/<id>.sh. Terpisah dari
     * berkas jalan ([stage]) karena berkas jalan memuat placeholder yang sudah terisi. Null bila ID tidak valid.
     */
    fun editFile(entryId: String): File? {
        val id = safeId(entryId)
        if (id.isEmpty()) return null
        return File(File(File(MolinaStorage.sharedRoot(), SCRIPTS_DIR_NAME), EDIT_DIR_NAME), "$id.sh")
    }

    /** Menulis isi mentah entri (placeholder tidak diisi) untuk disunting di editor; mengembalikan path berkas. */
    fun stageForEdit(entryId: String, content: String): LibraryResult<String> {
        val target = editFile(entryId) ?: return LibraryResult.Failure("ID entri tidak valid")
        val dir = target.parentFile ?: return LibraryResult.Failure("Folder berkas editor tidak valid")
        val temp = File(dir, target.name + ".tmp")
        return try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                return LibraryResult.Failure("Gagal membuat folder ${dir.path}; izin penyimpanan belum diberikan")
            }
            val text = content.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n') + "\n"
            temp.writeText(text, Charsets.UTF_8)
            if (temp.renameTo(target)) {
                LibraryResult.Ok(target.absolutePath)
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

    /** Menghapus salinan suntingan sebuah entri (dipanggil saat entri dihapus). */
    fun removeEditCopy(entryId: String): Boolean = try {
        editFile(entryId)?.delete() ?: false
    } catch (e: SecurityException) {
        false
    }
}
