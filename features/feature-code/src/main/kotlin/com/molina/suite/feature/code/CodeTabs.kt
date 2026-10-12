package com.molina.suite.feature.code

import java.io.File
import java.io.IOException

/**
 * Satu tab dokumen. [text], [line], dan [column] adalah salinan terakhir yang
 * disimpan; selama editor terpasang, editor yang menjadi acuan untuk tab aktif.
 * [revision] naik setiap isi diubah atau diganti, untuk mendeteksi perubahan yang
 * terjadi saat penyimpanan berlangsung. [untitledNumber] hanya bermakna selama
 * dokumen belum punya berkas. [syntax] bernilai null untuk penyorotan otomatis
 * menurut nama berkas, [CodeTabs.SYNTAX_PLAIN] untuk teks polos, atau nama bahasa
 * TextMate pilihan pengguna.
 */
internal data class CodeTab(
    val id: Long,
    val document: CodeDocument,
    val text: String,
    val line: Int,
    val column: Int,
    val revision: Int,
    val untitledNumber: Int,
    val syntax: String? = null
)

/** Data satu tab untuk bilah tab; judul diformat oleh view. */
internal data class CodeTabItem(
    val id: Long,
    val fileName: String?,
    val untitledNumber: Int,
    val dirty: Boolean
)

internal data class CodeTabBarState(val items: List<CodeTabItem>, val activeId: Long)

/**
 * Daftar tab dokumen dan tab yang aktif. Logika murni tanpa Android; tidak pernah
 * kosong (menutup tab terakhir menghasilkan dokumen "Tidak berjudul" baru).
 */
internal class CodeTabs(private val maxTabs: Int = DEFAULT_MAX_TABS) {

    private var nextId = 1L
    private var tabs: List<CodeTab> = listOf(newUntitled(emptyList()))

    var activeId: Long = tabs.first().id
        private set

    val limit: Int
        get() = maxTabs

    val active: CodeTab
        get() = tabs.first { it.id == activeId }

    fun find(id: Long): CodeTab? = tabs.firstOrNull { it.id == id }

    fun findByFile(file: File): CodeTab? {
        val key = pathKey(file)
        return tabs.firstOrNull { tab -> tab.document.file?.let { pathKey(it) == key } == true }
    }

    /** Dokumen kosong yang belum pernah disimpan atau diubah; boleh dipakai ulang untuk berkas lain. */
    fun isPristine(tab: CodeTab): Boolean = tab.document.file == null && !tab.document.dirty

    /** True bila satu dokumen lagi bisa dipasang (tab aktif yang kosong ikut dihitung sebagai tempat). */
    fun canPlace(): Boolean = isPristine(active) || tabs.size < maxTabs

    /** Simpan teks dan kursor editor ke tab aktif. */
    fun capture(text: String, line: Int, column: Int) {
        replace(activeId) { it.copy(text = text, line = line, column = column) }
    }

    fun activate(id: Long): Boolean {
        if (find(id) == null) return false
        activeId = id
        return true
    }

    /**
     * Pasang dokumen hasil muat dan jadikan aktif. Tab aktif yang masih dokumen kosong
     * dipakai ulang; bila tidak, tab baru ditambahkan di akhir. Null bila daftar penuh.
     */
    fun place(document: CodeDocument, text: String): CodeTab? {
        val current = active
        if (isPristine(current)) {
            val reused = current.copy(
                document = document,
                text = text,
                line = 0,
                column = 0,
                revision = current.revision + 1,
                untitledNumber = 0,
                syntax = null
            )
            replace(current.id) { reused }
            return reused
        }
        if (tabs.size >= maxTabs) return null
        val tab = CodeTab(nextId++, document, text, 0, 0, 0, 0)
        tabs = tabs + tab
        activeId = tab.id
        return tab
    }

    /** Tambah dokumen "Tidak berjudul" dan aktifkan; tab aktif yang sudah kosong dikembalikan apa adanya. */
    fun addUntitled(): CodeTab? {
        val current = active
        if (isPristine(current)) return current
        if (tabs.size >= maxTabs) return null
        val tab = newUntitled(tabs)
        tabs = tabs + tab
        activeId = tab.id
        return tab
    }

    /** Ganti isi tab [id] dengan hasil muat ulang dari berkas; false bila tab sudah tidak ada. */
    fun reload(id: Long, document: CodeDocument, text: String, line: Int, column: Int): Boolean {
        if (find(id) == null) return false
        replace(id) {
            it.copy(
                document = document,
                text = text,
                line = line,
                column = column,
                revision = it.revision + 1
            )
        }
        return true
    }

    fun updateDocument(id: Long, transform: (CodeDocument) -> CodeDocument) {
        replace(id) { it.copy(document = transform(it.document)) }
    }

    fun markEdited(id: Long) {
        replace(id) { tab ->
            tab.copy(
                revision = tab.revision + 1,
                document = if (tab.document.dirty) tab.document else tab.document.copy(dirty = true)
            )
        }
    }

    fun setSyntax(id: Long, syntax: String?) {
        replace(id) { it.copy(syntax = syntax) }
    }

    /**
     * Tutup tab [id]. Bila yang ditutup tab aktif, tab di sebelah kanannya (atau yang terakhir)
     * menjadi aktif. Mengembalikan tab aktif sesudahnya, atau null bila [id] tidak ada.
     */
    fun close(id: Long): CodeTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        val remaining = tabs.filterIndexed { i, _ -> i != index }
        if (remaining.isEmpty()) {
            val fresh = newUntitled(remaining)
            tabs = listOf(fresh)
            activeId = fresh.id
            return fresh
        }
        tabs = remaining
        if (id == activeId) activeId = remaining[index.coerceAtMost(remaining.size - 1)].id
        return active
    }

    fun barState(): CodeTabBarState = CodeTabBarState(
        tabs.map { CodeTabItem(it.id, it.document.file?.name, it.untitledNumber, it.document.dirty) },
        activeId
    )

    private fun replace(id: Long, transform: (CodeTab) -> CodeTab) {
        tabs = tabs.map { if (it.id == id) transform(it) else it }
    }

    private fun newUntitled(existing: List<CodeTab>): CodeTab {
        val used = existing.filter { it.document.file == null }.map { it.untitledNumber }.toSet()
        var number = 1
        while (number in used) number++
        return CodeTab(nextId++, CodeDocument.untitled(), "", 0, 0, 0, number)
    }

    private fun pathKey(file: File): String =
        try {
            file.canonicalPath
        } catch (e: IOException) {
            file.absolutePath
        }

    companion object {
        const val DEFAULT_MAX_TABS = 12
        const val SYNTAX_PLAIN = ""
    }
}
