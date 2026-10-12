package com.molina.suite.feature.code

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Folder kerja yang sedang terbuka di tab Code. Path-nya diingat antar-peluncuran;
 * folder baru dipulihkan hanya bila masih ada dan bisa diakses.
 */
internal class CodeWorkspace(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableTree = MutableStateFlow<CodeFolderTree?>(null)
    val tree: StateFlow<CodeFolderTree?> = mutableTree.asStateFlow()

    init {
        restoreIfPossible()
    }

    fun open(folder: File) {
        prefs.edit().putString(KEY_ROOT, folder.path).apply()
        mutableTree.value = CodeFolderTree(folder)
    }

    fun close() {
        prefs.edit().remove(KEY_ROOT).apply()
        mutableTree.value = null
    }

    /**
     * Pulihkan folder terakhir bila belum ada folder terbuka. Dipanggil ulang saat tab tampil
     * karena izin penyimpanan bisa baru diberikan setelah aplikasi dimulai.
     */
    fun restoreIfPossible() {
        if (mutableTree.value != null) return
        val path = prefs.getString(KEY_ROOT, null) ?: return
        val folder = File(path)
        if (folder.isDirectory) mutableTree.value = CodeFolderTree(folder)
    }

    private companion object {
        const val PREFS_NAME = "molina_code_workspace"
        const val KEY_ROOT = "root"
    }
}
