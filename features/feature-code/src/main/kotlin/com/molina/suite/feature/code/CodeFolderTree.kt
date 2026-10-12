package com.molina.suite.feature.code

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Satu baris pohon direktori yang siap ditampilkan; [depth] dihitung dari folder akar. */
internal sealed interface CodeTreeRow {
    val depth: Int

    data class Entry(
        override val depth: Int,
        val file: File,
        val isDirectory: Boolean,
        val expanded: Boolean
    ) : CodeTreeRow

    /** Penanda bahwa folder berisi lebih banyak isi daripada batas tampilan. */
    data class More(override val depth: Int, val hidden: Int) : CodeTreeRow

    /** Folder yang sedang terbuka tidak bisa dibaca. */
    data class Unreadable(override val depth: Int) : CodeTreeRow
}

/**
 * Model pohon direktori: folder akar dan himpunan folder yang dibuka. Tidak mengenal view;
 * isi folder dibaca ulang setiap [rows] dipanggil sehingga tidak ada cache yang bisa usang.
 */
internal class CodeFolderTree(val root: File) {

    private val lock = Any()
    private val expanded = HashSet<String>()

    /** Buka atau tutup [directory]; folder akar selalu terbuka. */
    fun toggle(directory: File) {
        synchronized(lock) {
            if (directory.path == root.path) return
            if (!expanded.remove(directory.path)) expanded.add(directory.path)
        }
    }

    suspend fun rows(): List<CodeTreeRow> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val out = ArrayList<CodeTreeRow>()
            appendChildren(root, 0, out)
            out
        }
    }

    private fun appendChildren(directory: File, depth: Int, out: MutableList<CodeTreeRow>) {
        val children: Array<File>? = try {
            directory.listFiles()
        } catch (e: SecurityException) {
            null
        }
        if (children == null) {
            out.add(CodeTreeRow.Unreadable(depth))
            return
        }
        val sorted = children
            .map { Pair(it, it.isDirectory) }
            .sortedWith(compareBy({ !it.second }, { it.first.name.lowercase(Locale.ROOT) }))
        val shown = if (sorted.size > MAX_CHILDREN) sorted.subList(0, MAX_CHILDREN) else sorted
        for ((child, isDirectory) in shown) {
            val open = isDirectory && expanded.contains(child.path)
            out.add(CodeTreeRow.Entry(depth, child, isDirectory, open))
            if (open) appendChildren(child, depth + 1, out)
        }
        if (sorted.size > shown.size) out.add(CodeTreeRow.More(depth, sorted.size - shown.size))
    }

    private companion object {
        const val MAX_CHILDREN = 2000
    }
}
