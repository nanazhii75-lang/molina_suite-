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
 * Sesi penyuntingan seumur proses: memegang dokumen (selalu ada; awalnya
 * "Tidak berjudul") dan menghubungkannya ke [CodeEditor] yang sedang terpasang.
 * View boleh dibuat ulang (rotasi, pindah tab); teks dipulihkan dari snapshot.
 * Semua metode dipanggil dari thread utama.
 */
internal class CodeEditorSession(
    private val files: CodeFileRepository,
    private val textMate: CodeTextMate
) {
    private class Snapshot(val text: String, val line: Int, val column: Int)

    private val mutableDocument = MutableStateFlow(CodeDocument.untitled())
    val document: StateFlow<CodeDocument> = mutableDocument.asStateFlow()

    private val mutableOptions = MutableStateFlow(CodeViewOptions())
    val options: StateFlow<CodeViewOptions> = mutableOptions.asStateFlow()

    private val mutableSearch = MutableStateFlow(CodeSearchState.NONE)
    val search: StateFlow<CodeSearchState> = mutableSearch.asStateFlow()

    private var editor: CodeEditor? = null
    private var snapshot = Snapshot("", 0, 0)
    private var applyingText = false
    private var revision = 0

    /** Pasang editor baru dan pulihkan dokumen yang sedang terbuka. */
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
        val saved = snapshot
        applyText(target, saved.text, saved.line, saved.column)
        applyHighlighting(target, mutableDocument.value.file)
    }

    /** Lepas editor; teks disimpan sebagai snapshot untuk editor berikutnya. */
    fun detach(target: CodeEditor) {
        if (editor !== target) return
        val cursor = target.cursor
        snapshot = Snapshot(target.text.toString(), cursor.leftLine, cursor.leftColumn)
        mutableSearch.value = CodeSearchState.NONE
        editor = null
    }

    /** Buka [file]; bila [keepCursor], posisi kursor dipertahankan (dipakai untuk muat ulang). */
    suspend fun open(file: File, keepCursor: Boolean = false): CodeFileLoad {
        val result = files.load(file)
        if (result !is CodeFileLoad.Loaded) return result
        val target = editor
        val line = if (keepCursor && target != null) target.cursor.leftLine else 0
        val column = if (keepCursor && target != null) target.cursor.leftColumn else 0
        revision++
        mutableDocument.value = CodeDocument(file, result.charset, result.hasBom, dirty = false)
        snapshot = Snapshot(result.text, line, column)
        stopSearch()
        if (target != null) {
            applyText(target, result.text, line, column)
            applyHighlighting(target, file)
        }
        return result
    }

    /** Muat ulang isi berkas dari penyimpanan dan buang perubahan di editor. */
    suspend fun reload(): CodeFileLoad {
        val file = mutableDocument.value.file
            ?: return CodeFileLoad.Failed("Dokumen belum disimpan, jadi tidak ada berkas untuk dimuat ulang")
        return open(file, keepCursor = true)
    }

    /** Ganti dokumen dengan dokumen kosong "Tidak berjudul". */
    suspend fun newDocument() {
        revision++
        mutableDocument.value = CodeDocument.untitled()
        snapshot = Snapshot("", 0, 0)
        stopSearch()
        editor?.let {
            applyText(it, "", 0, 0)
            applyHighlighting(it, null)
        }
    }

    suspend fun save(): CodeFileSave {
        val current = mutableDocument.value
        val file = current.file
            ?: return CodeFileSave.Failed("Dokumen belum punya nama berkas")
        return write(current, file)
    }

    suspend fun saveAs(file: File): CodeFileSave = write(mutableDocument.value, file)

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
        val text = target?.text?.toString() ?: snapshot.text
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

    private suspend fun write(current: CodeDocument, target: File): CodeFileSave {
        val text = editor?.text?.toString() ?: snapshot.text
        val savedRevision = revision
        val result = files.save(target, text, current.charset, current.hasBom)
        if (result is CodeFileSave.Saved) {
            val renamed = current.file != target
            mutableDocument.update { doc ->
                if (doc.file != current.file) {
                    doc
                } else {
                    doc.copy(file = target, dirty = doc.dirty && revision != savedRevision)
                }
            }
            if (renamed) editor?.let { applyHighlighting(it, target) }
        }
        return result
    }

    private fun publishSearch(target: CodeEditor) {
        val searcher = target.searcher
        if (!searcher.hasQuery()) return
        val total = searcher.matchedPositionCount
        val current = if (searcher.isMatchedPositionSelected) searcher.currentMatchedPositionIndex + 1 else 0
        mutableSearch.update { it.copy(total = total, current = current) }
    }

    private fun onContentEdited() {
        revision++
        mutableDocument.update { if (!it.dirty) it.copy(dirty = true) else it }
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

    private suspend fun applyHighlighting(target: CodeEditor, file: File?) {
        withContext(Dispatchers.Default) { textMate.warmUp() }
        if (target !== editor) return
        val highlighting = textMate.prepare(file?.name)
        highlighting.scheme?.let { target.setColorScheme(it) }
        target.setEditorLanguage(highlighting.language)
    }

    private companion object {
        const val TAG = "CodeEditorSession"
    }
}
