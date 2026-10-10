package com.molina.suite.feature.code

import android.util.Log
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.PatternSyntaxException

/**
 * Sesi penyuntingan seumur proses: memegang daftar tab dokumen (selalu ada minimal
 * satu; awalnya "Tidak berjudul") dan menghubungkan tab aktif ke [CodeEditor] yang
 * sedang terpasang. View boleh dibuat ulang (rotasi, pindah tab aplikasi); teks dan
 * kursor tiap tab dipulihkan dari salinan terakhirnya. Selama editor terpasang,
 * editor adalah acuan untuk teks dan kursor tab aktif.
 * Semua metode dipanggil dari thread utama.
 */
internal class CodeEditorSession(
    private val files: CodeFileRepository,
    private val textMate: CodeTextMate
) {
    private val tabs = CodeTabs()

    private val mutableDocument = MutableStateFlow(tabs.active.document)

    /** Dokumen pada tab yang sedang aktif. */
    val document: StateFlow<CodeDocument> = mutableDocument.asStateFlow()

    private val mutableTabBar = MutableStateFlow(tabs.barState())
    val tabBar: StateFlow<CodeTabBarState> = mutableTabBar.asStateFlow()

    private val mutableOptions = MutableStateFlow(CodeViewOptions())
    val options: StateFlow<CodeViewOptions> = mutableOptions.asStateFlow()

    private val mutableSearch = MutableStateFlow(CodeSearchState.NONE)
    val search: StateFlow<CodeSearchState> = mutableSearch.asStateFlow()

    private var editor: CodeEditor? = null
    private var applyingText = false

    fun tabLimitMessage(): String =
        "Terlalu banyak tab terbuka (maksimal ${tabs.limit}); tutup salah satu dulu"

    /** Pasang editor baru dan pulihkan tab yang sedang aktif. */
    suspend fun attach(target: CodeEditor) {
        editor = target
        target.subscribeEvent(
            ContentChangeEvent::class.java,
            EventReceiver<ContentChangeEvent> { event, _ ->
                if (target === editor && !applyingText &&
                    event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT
                ) {
                    onContentEdited()
                }
            }
        )
        target.subscribeEvent(
            PublishSearchResultEvent::class.java,
            EventReceiver<PublishSearchResultEvent> { _, _ ->
                if (target === editor) publishSearch(target)
            }
        )
        val current = mutableOptions.value
        target.setWordwrap(current.wordwrap)
        target.setEditable(!current.readOnly)
        target.searcher.setCyclicJumping(true)
        showActive(target)
    }

    /** Lepas editor; teks dan kursor tab aktif disimpan untuk editor berikutnya. */
    fun detach(target: CodeEditor) {
        if (editor !== target) return
        captureActive(target)
        mutableSearch.value = CodeSearchState.NONE
        editor = null
    }

    /**
     * Buka [file] di tab baru, atau pindah ke tabnya bila sudah terbuka. Null berarti berkas
     * sudah terbuka dan tabnya diaktifkan tanpa membaca ulang dari penyimpanan.
     */
    suspend fun open(file: File): CodeFileLoad? {
        tabs.findByFile(file)?.let {
            activateTab(it.id)
            return null
        }
        if (!tabs.canPlace()) return CodeFileLoad.Failed(tabLimitMessage())
        val result = files.load(file)
        if (result !is CodeFileLoad.Loaded) return result
        tabs.findByFile(file)?.let {
            activateTab(it.id)
            return null
        }
        editor?.let { captureActive(it) }
        val document = CodeDocument(file, result.charset, result.hasBom, dirty = false)
        if (tabs.place(document, result.text) == null) return CodeFileLoad.Failed(tabLimitMessage())
        stopSearch()
        publish()
        editor?.let { showActive(it) }
        return result
    }

    /** Muat ulang isi berkas pada tab aktif dari penyimpanan dan buang perubahan di editor. */
    suspend fun reload(): CodeFileLoad {
        val tab = tabs.active
        val file = tab.document.file
            ?: return CodeFileLoad.Failed("Dokumen belum disimpan, jadi tidak ada berkas untuk dimuat ulang")
        val result = files.load(file)
        if (result !is CodeFileLoad.Loaded) return result
        val target = editor
        val stillActive = tabs.activeId == tab.id
        val line = if (stillActive && target != null) target.cursor.leftLine else tab.line
        val column = if (stillActive && target != null) target.cursor.leftColumn else tab.column
        val document = CodeDocument(file, result.charset, result.hasBom, dirty = false)
        if (!tabs.reload(tab.id, document, result.text, line, column)) return result
        if (stillActive) stopSearch()
        publish()
        if (stillActive && target != null) {
            applyText(target, result.text, line, column)
            applyHighlighting(target)
        }
        return result
    }

    /** Buat tab "Tidak berjudul" baru; false bila daftar tab penuh. */
    suspend fun newDocument(): Boolean {
        if (tabs.isPristine(tabs.active)) return true
        editor?.let { captureActive(it) }
        if (tabs.addUntitled() == null) return false
        stopSearch()
        publish()
        editor?.let { showActive(it) }
        return true
    }

    /** Pindah ke tab [id]; teks dan kursor tab yang ditinggalkan disimpan dulu. */
    suspend fun activateTab(id: Long) {
        if (id == tabs.activeId || tabs.find(id) == null) return
        editor?.let { captureActive(it) }
        tabs.activate(id)
        stopSearch()
        publish()
        editor?.let { showActive(it) }
    }

    /** Tutup tab [id]. Pemanggil bertanggung jawab menanyakan perubahan yang belum disimpan. */
    suspend fun closeTab(id: Long) {
        if (tabs.find(id) == null) return
        val wasActive = id == tabs.activeId
        tabs.close(id)
        if (wasActive) stopSearch()
        publish()
        if (wasActive) editor?.let { showActive(it) }
    }

    suspend fun save(): CodeFileSave {
        val tab = tabs.active
        val file = tab.document.file
            ?: return CodeFileSave.Failed("Dokumen belum punya nama berkas")
        return write(tab, file)
    }

    suspend fun saveAs(file: File): CodeFileSave {
        val tab = tabs.active
        val other = tabs.findByFile(file)
        if (other != null && other.id != tab.id) {
            return CodeFileSave.Failed("Berkas itu sedang terbuka di tab lain; tutup tab tersebut dulu")
        }
        return write(tab, file)
    }

    fun undo() {
        editor?.let { if (it.canUndo()) it.undo() }
    }

    fun redo() {
        editor?.let { if (it.canRedo()) it.redo() }
    }

    fun moveCursorLeft() {
        editor?.moveSelection(SelectionMovement.LEFT)
    }

    fun moveCursorRight() {
        editor?.moveSelection(SelectionMovement.RIGHT)
    }

    fun paste() {
        editor?.pasteText()
    }

    fun setWordwrap(enabled: Boolean) {
        mutableOptions.update { it.copy(wordwrap = enabled) }
        editor?.setWordwrap(enabled)
    }

    fun setReadOnly(readOnly: Boolean) {
        mutableOptions.update { it.copy(readOnly = readOnly) }
        editor?.setEditable(!readOnly)
    }

    fun lineCount(): Int = editor?.text?.lineCount ?: 1

    /** Teks tab aktif saat ini; memakai salinan terakhir bila view belum terpasang. */
    fun currentText(): String = editor?.text?.toString() ?: tabs.active.text

    /** Daftar nama bahasa TextMate yang tersedia untuk pilihan Syntax. */
    fun syntaxNames(): List<String> = textMate.languageNames()

    /** Ganti bahasa penyorotan tab aktif; null berarti teks polos. False bila bahasa gagal dimuat. */
    fun setSyntax(name: String?): Boolean {
        val target = editor ?: return false
        val language: io.github.rosemoe.sora.lang.Language = if (name == null) {
            io.github.rosemoe.sora.lang.EmptyLanguage()
        } else {
            textMate.prepareByName(name) ?: return false
        }
        target.setEditorLanguage(language)
        tabs.setSyntax(tabs.activeId, name ?: CodeTabs.SYNTAX_PLAIN)
        return true
    }

    /**
     * Atur enkode yang dipakai saat menyimpan. False bila teks memuat karakter
     * yang tidak bisa dikodekan, supaya tidak ada data yang diam-diam rusak.
     */
    fun changeEncoding(charset: java.nio.charset.Charset, bom: Boolean): Boolean {
        val current = mutableDocument.value
        if (current.charset == charset && current.hasBom == bom) return true
        if (!charset.newEncoder().canEncode(currentText())) return false
        tabs.updateDocument(tabs.activeId) { it.copy(charset = charset, hasBom = bom, dirty = true) }
        publish()
        return true
    }

    /** Terapkan ukuran font dan nomor baris ke editor yang sedang terpasang. */
    fun applyAppearance(appearance: CodeAppearance) {
        val target = editor ?: return
        target.setTextSize(appearance.textSizeSp.toFloat())
        target.setLineNumberEnabled(appearance.lineNumbers)
        CodeThemeState.name = appearance.theme
        textMate.applyTheme()
    }

    /** Pindah ke awal baris [line] (berbasis satu); false bila di luar jangkauan. */
    fun goToLine(line: Int): Boolean {
        val target = editor ?: return false
        if (line < 1 || line > target.text.lineCount) return false
        target.jumpToLine(line - 1)
        return true
    }

    suspend fun statistics(): CodeTextStats {
        val doc = mutableDocument.value
        val target = editor
        val text = target?.text?.toString() ?: tabs.active.text
        val selected = target?.cursor?.let { if (it.isSelected) it.right - it.left else 0 } ?: 0
        return withContext(Dispatchers.Default) {
            CodeTextStatistics.compute(text, doc.charset, doc.hasBom, selected)
        }
    }

    /** Mulai atau perbarui pencarian; kueri kosong menghentikan pencarian. */
    fun search(request: CodeSearchRequest) {
        val target = editor ?: return
        if (request.query.isEmpty()) {
            stopSearch()
            return
        }
        val searcher = target.searcher
        searcher.setCyclicJumping(request.wrapAround)
        try {
            searcher.search(
                request.query,
                EditorSearcher.SearchOptions(!request.caseSensitive, request.regex)
            )
            mutableSearch.value = CodeSearchState(request.query, 0, 0)
        } catch (e: PatternSyntaxException) {
            if (searcher.hasQuery()) searcher.stopSearch()
            mutableSearch.value = CodeSearchState(request.query, 0, 0, invalidPattern = true)
        }
    }

    /** Lompat ke kecocokan berikutnya dari posisi kursor. */
    fun searchNext() {
        val target = editor ?: return
        if (!target.searcher.hasQuery()) return
        target.searcher.gotoNext()
        publishSearch(target)
    }

    /** Ganti kecocokan yang sedang dipilih; false bila editor hanya-baca atau belum ada pencarian aktif. */
    fun replaceCurrent(replacement: String): Boolean {
        val target = editor ?: return false
        if (!target.isEditable || !target.searcher.hasQuery()) return false
        target.searcher.replaceThis(replacement)
        publishSearch(target)
        return true
    }

    /** Ganti semua kecocokan; false bila editor hanya-baca atau belum ada pencarian aktif. */
    fun replaceAll(replacement: String): Boolean {
        val target = editor ?: return false
        if (!target.isEditable || !target.searcher.hasQuery()) return false
        target.searcher.replaceAll(replacement)
        return true
    }

    fun stopSearch() {
        editor?.let { if (it.searcher.hasQuery()) it.searcher.stopSearch() }
        mutableSearch.value = CodeSearchState.NONE
    }

    private suspend fun write(tab: CodeTab, target: File): CodeFileSave {
        val text = editor?.text?.toString() ?: tab.text
        val savedRevision = tab.revision
        val result = files.save(target, text, tab.document.charset, tab.document.hasBom)
        if (result is CodeFileSave.Saved) {
            val latest = tabs.find(tab.id)
            if (latest != null && latest.document.file == tab.document.file) {
                val stillDirty = latest.document.dirty && latest.revision != savedRevision
                tabs.updateDocument(tab.id) { it.copy(file = target, dirty = stillDirty) }
                publish()
                if (tab.document.file != target && tabs.activeId == tab.id) {
                    editor?.let { applyHighlighting(it) }
                }
            }
        }
        return result
    }

    private fun publish() {
        mutableDocument.value = tabs.active.document
        mutableTabBar.value = tabs.barState()
    }

    private fun publishSearch(target: CodeEditor) {
        val searcher = target.searcher
        if (!searcher.hasQuery()) return
        val total = searcher.matchedPositionCount
        val current = if (searcher.isMatchedPositionSelected) searcher.currentMatchedPositionIndex + 1 else 0
        mutableSearch.update { it.copy(total = total, current = current) }
    }

    private fun onContentEdited() {
        val tab = tabs.active
        val wasDirty = tab.document.dirty
        tabs.markEdited(tab.id)
        if (!wasDirty) publish()
    }

    private fun captureActive(target: CodeEditor) {
        val cursor = target.cursor
        tabs.capture(target.text.toString(), cursor.leftLine, cursor.leftColumn)
    }

    private suspend fun showActive(target: CodeEditor) {
        val tab = tabs.active
        applyText(target, tab.text, tab.line, tab.column)
        applyHighlighting(target)
    }

    private fun applyText(target: CodeEditor, text: String, line: Int, column: Int) {
        applyingText = true
        try {
            target.setText(text)
        } finally {
            applyingText = false
        }
        val content = target.text
        val safeLine = line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
        val safeColumn = column.coerceIn(0, content.getColumnCount(safeLine))
        try {
            target.setSelection(safeLine, safeColumn)
        } catch (e: IndexOutOfBoundsException) {
            Log.w(TAG, "Posisi kursor tidak valid: $safeLine:$safeColumn", e)
        }
    }

    private suspend fun applyHighlighting(target: CodeEditor) {
        val tabId = tabs.activeId
        withContext(Dispatchers.Default) { textMate.warmUp() }
        if (target !== editor || tabs.activeId != tabId) return
        val tab = tabs.find(tabId) ?: return
        val choice = tab.syntax
        val highlighting = textMate.prepare(if (choice == null) tab.document.file?.name else null)
        highlighting.scheme?.let { target.setColorScheme(it) }
        val language = if (choice == null || choice == CodeTabs.SYNTAX_PLAIN) {
            highlighting.language
        } else {
            textMate.prepareByName(choice) ?: highlighting.language
        }
        target.setEditorLanguage(language)
    }

    private companion object {
        const val TAG = "CodeEditorSession"
    }
}
